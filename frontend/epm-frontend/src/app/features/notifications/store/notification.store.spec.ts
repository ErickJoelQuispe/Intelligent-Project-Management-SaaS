import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
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
  };

  let oauthMock: { getAccessToken: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    serviceMock = {
      getNotifications: vi.fn(),
      getUnreadCount: vi.fn(),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
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
});
