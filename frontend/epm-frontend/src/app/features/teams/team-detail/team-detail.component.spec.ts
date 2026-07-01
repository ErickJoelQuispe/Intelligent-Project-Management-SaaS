import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { TeamDetailComponent } from './team-detail.component';
import { TeamService } from '../team.service';
import { UserService } from '../../settings/services/user.service';
import { InvitationService } from '../../registration/invitation.service';
import { Team } from '../../../core/models/team.model';
import { provideTranslocoTesting } from '../../../testing/transloco-testing';

const OWNER_ID = 'owner-001';
const MEMBER_ID = 'member-002';

const mockTeam: Team = {
  id: 'team-001',
  tenantId: 'tenant-001',
  ownerId: OWNER_ID,
  name: 'Alpha Team',
  description: 'A cool team',
  members: [
    {
      userId: OWNER_ID,
      role: 'OWNER',
      joinedAt: '2026-01-01T00:00:00Z',
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@example.com',
    },
    {
      userId: MEMBER_ID,
      role: 'MEMBER',
      joinedAt: '2026-01-02T00:00:00Z',
      firstName: 'Bob',
      lastName: 'Jones',
      email: 'bob@example.com',
    },
  ],
};

function buildTeamServiceMock(overrides?: {
  getById?: ReturnType<typeof vi.fn>;
  update?: ReturnType<typeof vi.fn>;
  updateMemberRole?: ReturnType<typeof vi.fn>;
  addMember?: ReturnType<typeof vi.fn>;
  removeMember?: ReturnType<typeof vi.fn>;
  delete?: ReturnType<typeof vi.fn>;
}) {
  return {
    getById: overrides?.getById ?? vi.fn().mockReturnValue(of(mockTeam)),
    update: overrides?.update ?? vi.fn().mockReturnValue(of({ ...mockTeam, name: 'New Name' })),
    updateMemberRole: overrides?.updateMemberRole ?? vi.fn().mockReturnValue(of(mockTeam)),
    addMember: overrides?.addMember ?? vi.fn().mockReturnValue(of(void 0)),
    removeMember: overrides?.removeMember ?? vi.fn().mockReturnValue(of(void 0)),
    delete: overrides?.delete ?? vi.fn().mockReturnValue(of(void 0)),
  };
}

function buildOAuthMock(sub: string) {
  return {
    getIdentityClaims: vi.fn().mockReturnValue({ sub }),
  };
}

async function createFixture(
  teamServiceMock: ReturnType<typeof buildTeamServiceMock>,
  oauthMock: ReturnType<typeof buildOAuthMock>,
  options: {
    tenantUsers?: { id: string; email: string; firstName: string | null; lastName: string | null }[];
    invitationServiceMock?: { inviteMember: ReturnType<typeof vi.fn> };
  } = {},
): Promise<ComponentFixture<TeamDetailComponent>> {
  const userServiceMock = {
    listTenantUsers: vi.fn().mockReturnValue(of(options.tenantUsers ?? [])),
  };

  const defaultInvitationServiceMock = {
    inviteMember: vi.fn().mockReturnValue(of({ invitationId: 'inv-1', email: 'new@example.com', expiresAt: '2026-07-02T00:00:00Z' })),
  };

  await TestBed.configureTestingModule({
    imports: [TeamDetailComponent],
    providers: [
      provideRouter([]),
      provideAnimations(),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: TeamService, useValue: teamServiceMock },
      { provide: UserService, useValue: userServiceMock },
      { provide: OAuthService, useValue: oauthMock },
      { provide: InvitationService, useValue: options.invitationServiceMock ?? defaultInvitationServiceMock },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: { get: () => 'team-001' } } },
      },
      ...provideTranslocoTesting(),
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(TeamDetailComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('TeamDetailComponent', () => {

  describe('owner actions — inline edit name', () => {
    it('owner clicks name → editingName() becomes true', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      expect(component.editingName()).toBe(false);
      component.startEditName();
      fixture.detectChanges();

      expect(component.editingName()).toBe(true);
    });

    it('commitName() calls teamService.update with correct payload', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      component.startEditName();
      component.editNameValue = 'New Team Name';
      component.commitName();

      expect(teamSvc.update).toHaveBeenCalledWith('team-001', { name: 'New Team Name' });
    });

    it('commitName() resets editingName to false', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      component.startEditName();
      component.editNameValue = 'New Name';
      component.commitName();

      expect(component.editingName()).toBe(false);
    });

    it('commitName() does NOT call service when name is unchanged', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      // clear previous calls (getById was called in constructor)
      teamSvc.update.mockClear();

      component.startEditName();
      component.editNameValue = 'Alpha Team'; // same as original
      component.commitName();

      expect(teamSvc.update).not.toHaveBeenCalled();
    });

    it('cancelName() sets editingName to false without calling service', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      teamSvc.update.mockClear();

      component.startEditName();
      expect(component.editingName()).toBe(true);

      component.cancelName();
      expect(component.editingName()).toBe(false);
      expect(teamSvc.update).not.toHaveBeenCalled();
    });
  });

  describe('owner actions — inline edit description', () => {
    it('owner clicks description → editingDesc() becomes true', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      expect(component.editingDesc()).toBe(false);
      component.startEditDesc();
      fixture.detectChanges();

      expect(component.editingDesc()).toBe(true);
    });

    it('commitDesc() calls teamService.update with new description', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      component.startEditDesc();
      component.editDescValue = 'Updated description';
      component.commitDesc();

      expect(teamSvc.update).toHaveBeenCalledWith('team-001', { description: 'Updated description' });
    });

    it('cancelDesc() sets editingDesc to false without calling service', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      teamSvc.update.mockClear();

      component.startEditDesc();
      component.cancelDesc();

      expect(component.editingDesc()).toBe(false);
      expect(teamSvc.update).not.toHaveBeenCalled();
    });
  });

  describe('owner actions — role change', () => {
    it('onRoleChange() calls teamService.updateMemberRole with correct payload', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      const memberFixture = mockTeam.members[1]; // Bob — MEMBER
      component.onRoleChange(memberFixture, 'VIEWER');

      expect(teamSvc.updateMemberRole).toHaveBeenCalledWith(
        'team-001',
        MEMBER_ID,
        { role: 'VIEWER' },
      );
    });

    it('onRoleChange() sets changingRoleFor to null after success', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      const memberFixture = mockTeam.members[1];
      component.onRoleChange(memberFixture, 'VIEWER');

      // updateMemberRole returns of(mockTeam) synchronously in mock
      expect(component.changingRoleFor()).toBeNull();
    });
  });

  describe('non-owner visibility', () => {
    it('non-owner: no inline-edit name input visible, h1 has no pointer cursor', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock('other-user-999'));
      const component = fixture.componentInstance;

      expect(component.isOwner()).toBe(false);

      // startEditName should not activate because guard is in template
      // but we verify signal logic: isOwner is false
      component.startEditName(); // method itself is public; isOwner guard is template-level
      fixture.detectChanges();

      // The input should NOT appear in the DOM because template guards on isOwner()
      const nameInput = fixture.nativeElement.querySelector('input[aria-label="Edit team name"]');
      // Non-owner: isOwner() is false so the @if block for input won't render
      expect(nameInput).toBeNull();
    });

    it('non-owner: no <select> role element in DOM', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock('other-user-999'));
      const component = fixture.componentInstance;

      expect(component.isOwner()).toBe(false);

      fixture.detectChanges();
      const roleSelect = fixture.nativeElement.querySelector('.td-role-select');
      expect(roleSelect).toBeNull();
    });

    it('non-owner: role badges visible for all members', async () => {
      const teamSvc = buildTeamServiceMock();
      const fixture = await createFixture(teamSvc, buildOAuthMock('other-user-999'));

      fixture.detectChanges();
      const roleBadges = fixture.nativeElement.querySelectorAll('app-team-role-badge');
      expect(roleBadges.length).toBeGreaterThan(0);
    });
  });

  describe('inlineSaveError display', () => {
    it('shows error when flushPending fails', async () => {
      const teamSvc = buildTeamServiceMock({
        update: vi.fn().mockReturnValue(throwError(() => new Error('Server error'))),
      });
      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID));
      const component = fixture.componentInstance;

      component.startEditName();
      component.editNameValue = 'Failed Save';
      component.commitName();
      fixture.detectChanges();

      expect(component.inlineSaveError()).toBe('Could not save. Please try again.');
    });
  });

  // ── Invite flow (PR-6) ─────────────────────────────────────────────────────

  describe('onAddMember() — email found in tenant → add-member flow', () => {
    it('calls teamService.addMember (not inviteMember) when email resolves to a known userId', async () => {
      const tenantUsers = [
        { id: 'user-existing-001', email: 'existing@example.com', firstName: 'Existing', lastName: 'User' },
      ];
      const invitationSvcMock = { inviteMember: vi.fn() };
      const teamSvc = buildTeamServiceMock({
        addMember: vi.fn().mockReturnValue(of(void 0)),
      });

      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID), {
        tenantUsers,
        invitationServiceMock: invitationSvcMock,
      });
      const component = fixture.componentInstance;

      component.addMemberForm.setValue({ email: 'existing@example.com', role: 'MEMBER' });
      component.onAddMember();

      expect(teamSvc.addMember).toHaveBeenCalledWith('team-001', { userId: 'user-existing-001', role: 'MEMBER' });
      expect(invitationSvcMock.inviteMember).not.toHaveBeenCalled();
    });
  });

  describe('onAddMember() — email NOT found in tenant → invite flow', () => {
    it('calls inviteMember when email is not in tenant user list', async () => {
      const invitationSvcMock = {
        inviteMember: vi.fn().mockReturnValue(of({ invitationId: 'inv-1', email: 'new@example.com', expiresAt: '2026-07-02T00:00:00Z' })),
      };
      const teamSvc = buildTeamServiceMock();

      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID), {
        tenantUsers: [],
        invitationServiceMock: invitationSvcMock,
      });
      const component = fixture.componentInstance;

      component.addMemberForm.setValue({ email: 'new@example.com', role: 'MEMBER' });
      component.onAddMember();

      expect(invitationSvcMock.inviteMember).toHaveBeenCalledWith('team-001', 'new@example.com');
    });

    it('sets memberError with success message after 201 invite response', async () => {
      const invitationSvcMock = {
        inviteMember: vi.fn().mockReturnValue(of({ invitationId: 'inv-1', email: 'new@example.com', expiresAt: '2026-07-02T00:00:00Z' })),
      };
      const teamSvc = buildTeamServiceMock();

      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID), {
        tenantUsers: [],
        invitationServiceMock: invitationSvcMock,
      });
      const component = fixture.componentInstance;

      component.addMemberForm.setValue({ email: 'new@example.com', role: 'MEMBER' });
      component.onAddMember();

      // After success, we show a toast/success message in memberError
      expect(component.inviteSuccess()).toContain('new@example.com');
    });

    it('sets memberError to "already pending" message on 409 invite response', async () => {
      const invitationSvcMock = {
        inviteMember: vi.fn().mockReturnValue(throwError(() => ({ status: 409 }))),
      };
      const teamSvc = buildTeamServiceMock();

      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID), {
        tenantUsers: [],
        invitationServiceMock: invitationSvcMock,
      });
      const component = fixture.componentInstance;

      component.addMemberForm.setValue({ email: 'dup@example.com', role: 'MEMBER' });
      component.onAddMember();

      expect(component.memberError()).toContain('pending');
    });

    it('sets memberError to "admins can invite" message on 403 invite response', async () => {
      const invitationSvcMock = {
        inviteMember: vi.fn().mockReturnValue(throwError(() => ({ status: 403 }))),
      };
      const teamSvc = buildTeamServiceMock();

      const fixture = await createFixture(teamSvc, buildOAuthMock(OWNER_ID), {
        tenantUsers: [],
        invitationServiceMock: invitationSvcMock,
      });
      const component = fixture.componentInstance;

      component.addMemberForm.setValue({ email: 'nonadmin@example.com', role: 'MEMBER' });
      component.onAddMember();

      expect(component.memberError()).toContain('admin');
    });
  });

});
