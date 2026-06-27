import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TeamService } from './team.service';
import { Team, UpdateTeamRequest, UpdateMemberRoleRequest } from '../../core/models/team.model';

const BASE_URL = 'http://localhost:8080/api/v1/teams';

const mockTeam: Team = {
  id: 'team-001',
  tenantId: 'tenant-001',
  ownerId: 'user-001',
  name: 'Alpha Team',
  description: 'A test team',
  members: [
    {
      userId: 'user-001',
      role: 'OWNER',
      joinedAt: '2026-01-01T00:00:00Z',
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@example.com',
    },
    {
      userId: 'user-002',
      role: 'MEMBER',
      joinedAt: '2026-01-02T00:00:00Z',
      firstName: 'Bob',
      lastName: 'Jones',
      email: 'bob@example.com',
    },
  ],
};

describe('TeamService', () => {
  let service: TeamService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TeamService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(TeamService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('update()', () => {
    it('should call PATCH /api/v1/teams/:id with the request body and return Team', () => {
      const req: UpdateTeamRequest = { name: 'New Name' };
      let result: Team | undefined;

      service.update('team-001', req).subscribe(t => (result = t));

      const httpReq = httpMock.expectOne(`${BASE_URL}/team-001`);
      expect(httpReq.request.method).toBe('PATCH');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush({ ...mockTeam, name: 'New Name' });

      expect(result).toBeDefined();
      expect(result!.name).toBe('New Name');
    });

    it('should call PATCH with description-only payload', () => {
      const req: UpdateTeamRequest = { description: 'Updated desc' };

      service.update('team-001', req).subscribe();

      const httpReq = httpMock.expectOne(`${BASE_URL}/team-001`);
      expect(httpReq.request.method).toBe('PATCH');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(mockTeam);
    });
  });

  describe('updateMemberRole()', () => {
    it('should call PATCH /api/v1/teams/:teamId/members/:userId with role body', () => {
      const req: UpdateMemberRoleRequest = { role: 'VIEWER' };
      let result: Team | undefined;

      service.updateMemberRole('team-001', 'user-002', req).subscribe(t => (result = t));

      const httpReq = httpMock.expectOne(`${BASE_URL}/team-001/members/user-002`);
      expect(httpReq.request.method).toBe('PATCH');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(mockTeam);

      expect(result).toBeDefined();
    });

    it('should call PATCH to change role from VIEWER to MEMBER', () => {
      const req: UpdateMemberRoleRequest = { role: 'MEMBER' };

      service.updateMemberRole('team-001', 'user-002', req).subscribe();

      const httpReq = httpMock.expectOne(`${BASE_URL}/team-001/members/user-002`);
      expect(httpReq.request.method).toBe('PATCH');
      expect(httpReq.request.body).toEqual({ role: 'MEMBER' });
      httpReq.flush(mockTeam);
    });
  });
});
