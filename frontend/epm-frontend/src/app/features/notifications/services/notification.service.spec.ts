import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { RxStomp } from '@stomp/rx-stomp';
import { NotificationService } from './notification.service';
import { Notification } from '../models/notification.model';

// ─── RxStomp module mock ──────────────────────────────────────────────────────
// Using async factory so the MockRxStompClass IS a proper constructor.

vi.mock('@stomp/rx-stomp', async () => {
  class MockRxStompClass {
    configure = vi.fn();
    activate = vi.fn();
    deactivate = vi.fn().mockResolvedValue(undefined);
    watch = vi.fn();
  }
  return {
    RxStomp: MockRxStompClass,
    RxStompConfig: class {},
  };
});

// Helper that returns the mock instance from the service
function stomp(service: NotificationService) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return service.getRxStomp() as any;
}

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotificationService,
      ],
    });

    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ─── HTTP methods (regression guard) ────────────────────────────────────────

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getNotifications() calls GET /api/v1/notifications and returns list', () => {
    const mockNotifs: Notification[] = [
      { id: 'n1', type: 'TASK_CREATED', referenceId: 't1', message: 'Created', read: false, createdAt: '2026-06-04T12:00:00Z' },
    ];

    let result: Notification[] | undefined;
    service.getNotifications().subscribe((r) => (result = r));

    const req = httpMock.expectOne('http://localhost:8080/api/v1/notifications');
    expect(req.request.method).toBe('GET');
    req.flush(mockNotifs);

    expect(result).toEqual(mockNotifs);
    expect(result![0].id).toBe('n1');
  });

  it('getUnreadCount() calls GET /api/v1/notifications/unread-count and returns number', () => {
    let count: number | undefined;
    service.getUnreadCount().subscribe((c) => (count = c));

    const req = httpMock.expectOne('http://localhost:8080/api/v1/notifications/unread-count');
    expect(req.request.method).toBe('GET');
    req.flush(3);

    expect(count).toBe(3);
  });

  it('markAsRead() calls PATCH /api/v1/notifications/{id}/read', () => {
    const mockNotif: Notification = {
      id: 'n1', type: 'TASK_ASSIGNED', referenceId: 't1', message: 'Assigned', read: true, createdAt: '2026-06-04T12:00:00Z'
    };

    let result: Notification | undefined;
    service.markAsRead('n1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('http://localhost:8080/api/v1/notifications/n1/read');
    expect(req.request.method).toBe('PATCH');
    req.flush(mockNotif);

    expect(result!.read).toBe(true);
    expect(result!.id).toBe('n1');
  });

  it('markAllAsRead() calls POST /api/v1/notifications/mark-all-read', () => {
    service.markAllAsRead().subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/notifications/mark-all-read');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  // ─── WebSocket connect / disconnect ─────────────────────────────────────────

  it('connect(userId, token) creates RxStomp and activates with correct WS URL + token', () => {
    service.connect('user-123', 'jwt-abc');

    const rx = stomp(service);
    expect(rx).not.toBeNull();
    expect(rx.configure).toHaveBeenCalledOnce();

    const config = rx.configure.mock.calls[0][0];
    expect(config.brokerURL).toContain('ws://localhost:8080/ws/notifications');
    expect(config.brokerURL).toContain('token=jwt-abc');
    expect(rx.activate).toHaveBeenCalledOnce();
  });

  it('connect() with a different token produces a different brokerURL', () => {
    service.connect('user-B', 'token-xyz');

    const config = stomp(service).configure.mock.calls[0][0];
    expect(config.brokerURL).toContain('token=token-xyz');
    expect(config.brokerURL).not.toContain('token=jwt-abc');
  });

  it('disconnect() calls deactivate on the RxStomp instance', () => {
    service.connect('user-123', 'jwt-abc');
    service.disconnect();

    expect(stomp(service).deactivate).toHaveBeenCalledOnce();
  });

  it('disconnect() is a no-op when connect() was never called', () => {
    // Should not throw when called before connect()
    expect(() => service.disconnect()).not.toThrow();
    expect(service.getRxStomp()).toBeNull();
  });

  it('getNotificationStream() watches the correct user topic on STOMP', () => {
    const subject = new Subject();
    service.connect('user-123', 'jwt-abc');
    stomp(service).watch.mockReturnValue(subject.asObservable());

    const stream$ = service.getNotificationStream('user-123');

    expect(stomp(service).watch).toHaveBeenCalledWith('/topic/notifications/user-123');
    expect(stream$).toBeDefined();
  });
});
