import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { NotificationStore } from './notification.store';
import { NotificationService } from '../services/notification.service';
import { Notification } from '../models/notification.model';
import { provideTranslocoTesting } from '../../../testing/transloco-testing';

const mockNotification = (overrides: Partial<Notification> = {}): Notification => ({
  id: 'n1',
  type: 'TASK_CREATED',
  referenceId: 'task-1',
  message: 'Task created',
  read: false,
  createdAt: '2026-06-04T12:00:00Z',
  ...overrides,
});

describe('NotificationStore', () => {
  let store: InstanceType<typeof NotificationStore>;
  let serviceMock: {
    getNotifications: ReturnType<typeof vi.fn>;
    getUnreadCount: ReturnType<typeof vi.fn>;
    markAsRead: ReturnType<typeof vi.fn>;
    markAllAsRead: ReturnType<typeof vi.fn>;
    connect: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
    getNotificationStream: ReturnType<typeof vi.fn>;
    connected$: Subject<void>;
  };

  let oauthMock: { getAccessToken: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    const connectedSubject = new Subject<void>();

    serviceMock = {
      getNotifications: vi.fn(),
      getUnreadCount: vi.fn(),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
      connect: vi.fn(),
      disconnect: vi.fn(),
      getNotificationStream: vi.fn(),
      connected$: connectedSubject,
    };

    oauthMock = {
      getAccessToken: vi.fn().mockReturnValue('mock-token'),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotificationStore,
        { provide: NotificationService, useValue: serviceMock },
        { provide: OAuthService, useValue: oauthMock },
        ...provideTranslocoTesting(),
      ],
    });

    store = TestBed.inject(NotificationStore);
  });

  it('should initialize with empty state', () => {
    expect(store.notifications()).toEqual([]);
    expect(store.unreadCount()).toBe(0);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('should expose wsConnected signal initialized to false', () => {
    expect(store.wsConnected()).toBe(false);
  });

  it('loadNotifications() populates notifications signal on success', () => {
    const mockNotifs = [mockNotification(), mockNotification({ id: 'n2', read: true })];
    serviceMock.getNotifications.mockReturnValue(of(mockNotifs));
    serviceMock.getUnreadCount.mockReturnValue(of(1));

    TestBed.runInInjectionContext(() => {
      store.loadNotifications();
    });

    expect(serviceMock.getNotifications).toHaveBeenCalled();
    expect(store.notifications().length).toBe(2);
    expect(store.notifications()[0].id).toBe('n1');
    expect(store.loading()).toBe(false);
  });

  it('loadNotifications() sets error on failure', () => {
    serviceMock.getNotifications.mockReturnValue(throwError(() => new Error('Network error')));

    TestBed.runInInjectionContext(() => {
      store.loadNotifications();
    });

    expect(store.error()).toBe('Network error');
    expect(store.loading()).toBe(false);
  });

  it('loadNotifications() called twice cancels the first request (switchMap)', () => {
    // Bug 4 regression: concurrent calls must not race — switchMap cancels in-flight
    const firstSubject = new Subject<Notification[]>();
    const secondSubject = new Subject<Notification[]>();

    serviceMock.getNotifications
      .mockReturnValueOnce(firstSubject.asObservable())
      .mockReturnValueOnce(secondSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      store.loadNotifications(); // first call — in-flight
      store.loadNotifications(); // second call — should cancel the first
    });

    // Complete the second request — first was cancelled by switchMap
    secondSubject.next([mockNotification({ id: 'n-second' })]);
    secondSubject.complete();

    // Only the second result should be in the store
    expect(store.notifications().length).toBe(1);
    expect(store.notifications()[0].id).toBe('n-second');
  });

  it('markAsRead() updates the notification to read and decrements unreadCount', () => {
    const unread = mockNotification({ id: 'n1', read: false });
    serviceMock.getNotifications.mockReturnValue(of([unread]));
    serviceMock.getUnreadCount.mockReturnValue(of(1));
    serviceMock.markAsRead.mockReturnValue(of({ ...unread, read: true }));

    TestBed.runInInjectionContext(() => {
      store.loadNotifications();
      store.markAsRead('n1');
    });

    expect(serviceMock.markAsRead).toHaveBeenCalledWith('n1');
    expect(store.notifications()[0].read).toBe(true);
    expect(store.unreadCount()).toBe(0);
  });

  it('markAllAsRead() marks all notifications read and resets unreadCount to 0', () => {
    const unread1 = mockNotification({ id: 'n1', read: false });
    const unread2 = mockNotification({ id: 'n2', read: false });
    serviceMock.getNotifications.mockReturnValue(of([unread1, unread2]));
    serviceMock.getUnreadCount.mockReturnValue(of(2));
    serviceMock.markAllAsRead.mockReturnValue(of(void 0));

    TestBed.runInInjectionContext(() => {
      store.loadNotifications();
      store.markAllAsRead();
    });

    expect(serviceMock.markAllAsRead).toHaveBeenCalled();
    expect(store.notifications().every((n) => n.read)).toBe(true);
    expect(store.unreadCount()).toBe(0);
  });

  // ─── WebSocket tests ─────────────────────────────────────────────────────────

  it('pollNotifications does NOT exist on the store', () => {
    // The method was removed; accessing it should be undefined
    expect((store as any).pollNotifications).toBeUndefined();
  });

  it('connectWebSocket() calls service.connect with userId and token', () => {
    const messageSubject = new Subject<{ body: string }>();
    serviceMock.getUnreadCount.mockReturnValue(of({ count: 0 }));
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'jwt-token');
    });

    expect(serviceMock.connect).toHaveBeenCalledWith('user-42', 'jwt-token');
    expect(serviceMock.getNotificationStream).toHaveBeenCalledWith('user-42');
  });

  // Bug 3 — wsConnected timing tests
  it('connectWebSocket() does NOT set wsConnected to true immediately (before STOMP handshake)', () => {
    const messageSubject = new Subject<{ body: string }>();
    serviceMock.getUnreadCount.mockReturnValue(of({ count: 0 }));
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());
    // connected$ is a Subject — it will NOT emit immediately

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'jwt-token');
    });

    // Before connected$ emits, wsConnected must still be false
    expect(store.wsConnected()).toBe(false);
  });

  it('connectWebSocket() sets wsConnected to true only after connected$ emits', () => {
    const messageSubject = new Subject<{ body: string }>();
    serviceMock.getUnreadCount.mockReturnValue(of({ count: 0 }));
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'jwt-token');
    });

    expect(store.wsConnected()).toBe(false); // not yet connected

    // Simulate STOMP CONNECTED frame
    serviceMock.connected$.next();

    expect(store.wsConnected()).toBe(true);
  });

  it('incoming STOMP message prepends notification and increments unreadCount', () => {
    const messageSubject = new Subject<{ body: string }>();
    serviceMock.getUnreadCount.mockReturnValue(of({ count: 0 }));
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());
    // Emit connected so state is ready
    serviceMock.connected$ = new Subject<void>();

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'jwt-token');
    });

    const newNotification = mockNotification({ id: 'n-ws', message: 'WS notification', read: false });

    messageSubject.next({ body: JSON.stringify(newNotification) });

    expect(store.notifications().length).toBe(1);
    expect(store.notifications()[0].id).toBe('n-ws');
    expect(store.unreadCount()).toBe(1);
  });

  it('incoming STOMP message prepends to existing list (most recent first)', () => {
    const existing = mockNotification({ id: 'n-existing', read: true });
    serviceMock.getNotifications.mockReturnValue(of([existing]));
    serviceMock.getUnreadCount.mockReturnValue(of(0));
    const messageSubject = new Subject<{ body: string }>();
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      store.loadNotifications();
      store.connectWebSocket('user-42', 'jwt-token');
    });

    const newNotification = mockNotification({ id: 'n-new', read: false });
    messageSubject.next({ body: JSON.stringify(newNotification) });

    expect(store.notifications().length).toBe(2);
    expect(store.notifications()[0].id).toBe('n-new'); // newest first
    expect(store.notifications()[1].id).toBe('n-existing');
    expect(store.unreadCount()).toBe(1);
  });

  it('calling connectWebSocket again with new token disconnects and reconnects', () => {
    const subject1 = new Subject<{ body: string }>();
    const subject2 = new Subject<{ body: string }>();
    serviceMock.getUnreadCount.mockReturnValue(of({ count: 0 }));
    serviceMock.getNotificationStream
      .mockReturnValueOnce(subject1.asObservable())
      .mockReturnValueOnce(subject2.asObservable());

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'old-token');
    });

    // Simulate STOMP handshake completing so wsConnected becomes true —
    // the disconnect guard only fires when the store knows it's connected.
    serviceMock.connected$.next();
    expect(store.wsConnected()).toBe(true);

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'new-token');
    });

    expect(serviceMock.disconnect).toHaveBeenCalledOnce();
    expect(serviceMock.connect).toHaveBeenCalledTimes(2);
    expect(serviceMock.connect).toHaveBeenLastCalledWith('user-42', 'new-token');
  });
});
