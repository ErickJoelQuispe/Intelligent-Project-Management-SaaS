import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NotificationService } from './notification.service';
import { Notification } from '../models/notification.model';

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
});
