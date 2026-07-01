import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { InvitationService } from './invitation.service';

const BASE_URL = 'http://localhost:8080/api/v1';

describe('InvitationService', () => {
  let service: InvitationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        InvitationService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(InvitationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ---------------------------------------------------------------------------
  // inviteMember()
  // ---------------------------------------------------------------------------

  describe('inviteMember()', () => {
    it('POSTs to the correct URL with the email body', () => {
      let result: { invitationId: string; email: string; expiresAt: string } | undefined;

      service.inviteMember('team-001', 'alice@example.com').subscribe(r => (result = r));

      const req = httpMock.expectOne(`${BASE_URL}/teams/team-001/invite`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'alice@example.com' });

      const mockResponse = {
        invitationId: 'inv-123',
        email: 'alice@example.com',
        expiresAt: '2026-07-02T12:00:00Z',
      };
      req.flush(mockResponse, { status: 201, statusText: 'Created' });

      expect(result).toEqual(mockResponse);
    });

    it('POSTs to the correct URL for a different teamId and email', () => {
      service.inviteMember('team-999', 'bob@example.com').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/teams/team-999/invite`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'bob@example.com' });
      req.flush({ invitationId: 'inv-456', email: 'bob@example.com', expiresAt: '2026-07-02T12:00:00Z' });
    });
  });

  // ---------------------------------------------------------------------------
  // acceptInvitation()
  // ---------------------------------------------------------------------------

  describe('acceptInvitation()', () => {
    it('POSTs to the correct URL with all required fields', () => {
      service.acceptInvitation('token-abc', 'Alice', 'Smith', 'secret123').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/auth/accept-invitation`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        token: 'token-abc',
        firstName: 'Alice',
        lastName: 'Smith',
        password: 'secret123',
      });
      req.flush(null, { status: 201, statusText: 'Created' });
    });

    it('POSTs with different token and user data', () => {
      service.acceptInvitation('token-xyz', 'Bob', 'Jones', 'password99').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/auth/accept-invitation`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        token: 'token-xyz',
        firstName: 'Bob',
        lastName: 'Jones',
        password: 'password99',
      });
      req.flush(null, { status: 201, statusText: 'Created' });
    });
  });
});
