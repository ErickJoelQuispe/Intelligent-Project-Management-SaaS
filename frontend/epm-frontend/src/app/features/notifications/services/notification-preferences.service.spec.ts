import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NotificationPreferencesService } from './notification-preferences.service';
import { NotificationPreference } from '../models/notification.model';
import { provideTranslocoTesting } from '../../../testing/transloco-testing';

describe('NotificationPreferencesService', () => {
  let service: NotificationPreferencesService;
  let httpMock: HttpTestingController;

  const mockPreferences: NotificationPreference[] = [
    { eventType: 'TASK_CREATED', channel: 'IN_APP', enabled: true },
    { eventType: 'TASK_ASSIGNED', channel: 'EMAIL', enabled: false },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotificationPreferencesService,
        ...provideTranslocoTesting(),
      ],
    });

    service = TestBed.inject(NotificationPreferencesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getPreferences() calls GET /api/v1/notifications/preferences and returns list', () => {
    let result: NotificationPreference[] | undefined;
    service.getPreferences().subscribe((r) => (result = r));

    const req = httpMock.expectOne('http://localhost:8080/api/v1/notifications/preferences');
    expect(req.request.method).toBe('GET');
    req.flush(mockPreferences);

    expect(result).toEqual(mockPreferences);
    expect(result!.length).toBe(2);
    expect(result![0].eventType).toBe('TASK_CREATED');
    expect(result![1].enabled).toBe(false);
  });

  it('getPreferences() returns empty array when backend sends empty list', () => {
    let result: NotificationPreference[] | undefined;
    service.getPreferences().subscribe((r) => (result = r));

    const req = httpMock.expectOne('http://localhost:8080/api/v1/notifications/preferences');
    req.flush([]);

    expect(result).toEqual([]);
  });

  it('updatePreference() calls PUT /api/v1/notifications/preferences/{eventType}/{channel} with enabled body', () => {
    service.updatePreference('TASK_ASSIGNED', 'EMAIL', false).subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8080/api/v1/notifications/preferences/TASK_ASSIGNED/EMAIL',
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ enabled: false });
    req.flush(null, { status: 200, statusText: 'OK' });
  });

  it('updatePreference() with enabled=true sends correct body', () => {
    service.updatePreference('PROJECT_CREATED', 'IN_APP', true).subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8080/api/v1/notifications/preferences/PROJECT_CREATED/IN_APP',
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ enabled: true });
    req.flush(null, { status: 200, statusText: 'OK' });
  });
});
