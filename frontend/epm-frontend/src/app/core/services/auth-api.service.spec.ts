import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthApiService } from './auth-api.service';
import { environment } from '../../../environments/environment';
import { provideTranslocoTesting } from '../../testing/transloco-testing';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        ...provideTranslocoTesting(),
      ],
    });
    service = TestBed.inject(AuthApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ── RED: disableAccount() sends DELETE to correct endpoint ─────────────────

  it('disableAccount() sends DELETE to /api/v1/auth/account', () => {
    service.disableAccount().subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/account`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  // ── TRIANGULATE: disableAccount() completes without error on 204 ───────────

  it('disableAccount() emits and completes on 204', () => {
    let completed = false;

    service.disableAccount().subscribe({
      complete: () => { completed = true; },
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/account`);
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });
});
