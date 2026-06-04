import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { NotificationStore } from './notification.store';
import { NotificationService } from '../services/notification.service';
import { Notification } from '../models/notification.model';

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
  };

  let oauthMock: { getAccessToken: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    serviceMock = {
      getNotifications: vi.fn(),
      getUnreadCount: vi.fn(),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
      connect: vi.fn(),
      disconnect: vi.fn(),
      getNotificationStream: vi.fn(),
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
    const messageSubject = new Subject();
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'jwt-token');
    });

    expect(serviceMock.connect).toHaveBeenCalledWith('user-42', 'jwt-token');
    expect(serviceMock.getNotificationStream).toHaveBeenCalledWith('user-42');
  });

  it('connectWebSocket() sets wsConnected to true', () => {
    const messageSubject = new Subject();
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'jwt-token');
    });

    expect(store.wsConnected()).toBe(true);
  });

  it('incoming STOMP message prepends notification and increments unreadCount', () => {
    const messageSubject = new Subject<{ body: string }>();
    serviceMock.getNotificationStream.mockReturnValue(messageSubject.asObservable());

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
    serviceMock.getNotificationStream
      .mockReturnValueOnce(subject1.asObservable())
      .mockReturnValueOnce(subject2.asObservable());

    TestBed.runInInjectionContext(() => {
      store.connectWebSocket('user-42', 'old-token');
      store.connectWebSocket('user-42', 'new-token');
    });

    expect(serviceMock.disconnect).toHaveBeenCalledOnce();
    expect(serviceMock.connect).toHaveBeenCalledTimes(2);
    expect(serviceMock.connect).toHaveBeenLastCalledWith('user-42', 'new-token');
  });
});
