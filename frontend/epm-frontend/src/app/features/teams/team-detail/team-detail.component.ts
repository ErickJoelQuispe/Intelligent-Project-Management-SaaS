import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, FormsModule, Validators } from '@angular/forms';
import { OAuthService } from 'angular-oauth2-oidc';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { TeamService } from '../team.service';
import { UserService } from '../../settings/services/user.service';
import { InvitationService } from '../../registration/invitation.service';
import {
  Team,
  TeamMember,
  TeamRole,
  UpdateTeamRequest,
  UpdateMemberRoleRequest,
} from '../../../core/models/team.model';
import { TenantUser } from '../../../core/models/user-profile.model';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { TeamRoleBadgeComponent } from '../../../shared/components/team-role-badge/team-role-badge.component';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-team-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    FormsModule,
    TranslocoPipe,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    TeamRoleBadgeComponent,
    AvatarComponent,
  ],
  templateUrl: './team-detail.component.html',
})
export class TeamDetailComponent {
  private readonly route             = inject(ActivatedRoute);
  private readonly router            = inject(Router);
  private readonly teamService       = inject(TeamService);
  private readonly userService       = inject(UserService);
  private readonly invitationService = inject(InvitationService);
  private readonly oauth             = inject(OAuthService);
  private readonly fb                = inject(FormBuilder);
  private readonly confirmDialog     = inject(ConfirmDialogService);
  private readonly translocoService  = inject(TranslocoService);

  // ── Core state ──────────────────────────────────────────────────────────
  readonly team           = signal<Team | null>(null);
  readonly tenantUsers    = signal<TenantUser[]>([]);
  readonly loading        = signal(true);
  readonly error          = signal<string | null>(null);
  readonly addingMember   = signal(false);
  readonly removingMember = signal<string | null>(null);
  readonly deleting       = signal(false);
  readonly memberError    = signal<string | null>(null);
  /** Success message shown after a successful invitation (cleared on next add-member action) */
  readonly inviteSuccess  = signal<string | null>(null);

  // ── Inline edit state ───────────────────────────────────────────────────
  readonly editingName     = signal(false);
  readonly editingDesc     = signal(false);
  readonly savingInline    = signal(false);
  readonly inlineSaveError = signal<string | null>(null);
  readonly changingRoleFor = signal<string | null>(null);

  editNameValue = '';
  editDescValue = '';

  private pendingPatch: Partial<UpdateTeamRequest> | null = null;
  private isSaving = false;

  // ── Form ─────────────────────────────────────────────────────────────────
  readonly roleOptions: TeamRole[] = ['OWNER', 'MEMBER', 'VIEWER'];

  readonly addMemberForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role:  ['MEMBER' as TeamRole, Validators.required],
  });

  // ── Computed ─────────────────────────────────────────────────────────────
  readonly isOwner = computed(() => {
    const t = this.team();
    if (!t) return false;
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    const sub = claims?.['sub'] ?? '';
    return t.ownerId === sub;
  });

  readonly currentUserId = computed<string>(() => {
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    return claims?.['sub'] ?? '';
  });

  /** Tenant user map for email→userId resolution (add member form) */
  private readonly userMap = computed<Map<string, TenantUser>>(() => {
    const map = new Map<string, TenantUser>();
    for (const u of this.tenantUsers()) map.set(u.id, u);
    return map;
  });

  /** Tenant users not yet in this team — shown as quick-add suggestions */
  readonly suggestions = computed<TenantUser[]>(() => {
    const t = this.team();
    if (!t) return [];
    const memberIds = new Set(t.members.map(m => m.userId));
    return this.tenantUsers().filter(u => !memberIds.has(u.id)).slice(0, 8);
  });

  /** Resolve email typed in the form to a userId — called at submit time, not as computed */
  private resolveUserIdFromEmail(): string | null {
    const email = this.addMemberForm.controls.email.value?.trim().toLowerCase();
    if (!email) return null;
    const found = this.tenantUsers().find(u => u.email.toLowerCase() === email);
    return found?.id ?? null;
  }

  constructor() {
    const teamId = this.route.snapshot.paramMap.get('teamId');
    if (!teamId) {
      this.error.set(this.translocoService.translate('teams.detail.loadError'));
      this.loading.set(false);
      return;
    }
    this.loadTeam(teamId);
    this.userService.listTenantUsers().subscribe({
      next: users => this.tenantUsers.set(users),
      error: () => { /* non-critical — only needed for add-member resolution */ },
    });
  }

  private loadTeam(id: string, silent = false): void {
    if (!silent) this.loading.set(true);
    this.error.set(null);
    this.teamService.getById(id).subscribe({
      next:  t  => { this.team.set(t); this.loading.set(false); },
      error: () => { this.error.set(this.translocoService.translate('teams.detail.loadError')); this.loading.set(false); },
    });
  }

  private get teamId(): string {
    return this.team()?.id ?? this.route.snapshot.paramMap.get('teamId') ?? '';
  }

  // ── Inline edit — name ────────────────────────────────────────────────────
  startEditName(): void {
    this.editNameValue = this.team()?.name ?? '';
    this.editingName.set(true);
    this.inlineSaveError.set(null);
  }

  cancelName(): void {
    this.editingName.set(false);
  }

  commitName(): void {
    const name = this.editNameValue.trim();
    this.editingName.set(false);
    if (!name || name === this.team()?.name) return;
    this.saveInline({ name });
  }

  // ── Inline edit — description ─────────────────────────────────────────────
  startEditDesc(): void {
    this.editDescValue = this.team()?.description ?? '';
    this.editingDesc.set(true);
    this.inlineSaveError.set(null);
  }

  cancelDesc(): void {
    this.editingDesc.set(false);
  }

  commitDesc(): void {
    const description = this.editDescValue.trim();
    this.editingDesc.set(false);
    if (description === (this.team()?.description ?? '')) return;
    this.saveInline({ description });
  }

  // ── Save queue ────────────────────────────────────────────────────────────
  saveInline(patch: Partial<UpdateTeamRequest>): void {
    this.pendingPatch = { ...this.pendingPatch, ...patch };
    this.savingInline.set(true);
    if (!this.isSaving) {
      this.flushPending();
    }
  }

  flushPending(): void {
    if (!this.pendingPatch) {
      this.savingInline.set(false);
      return;
    }

    const t = this.team();
    if (!t) {
      this.pendingPatch = null;
      this.savingInline.set(false);
      return;
    }

    const patch = this.pendingPatch;
    this.pendingPatch = null;
    this.inlineSaveError.set(null);
    this.isSaving = true;

    this.teamService.update(this.teamId, patch).subscribe({
      next: updated => {
        this.team.set(updated);
        this.isSaving = false;
        this.flushPending();
      },
      error: () => {
        this.inlineSaveError.set(this.translocoService.translate('projects.detail.saveError'));
        this.isSaving = false;
        this.savingInline.set(false);
      },
    });
  }

  /** Display name for a TenantUser suggestion chip */
  suggestionLabel(u: TenantUser): string {
    const name = [u.firstName, u.lastName].filter(Boolean).join(' ');
    return name || u.email;
  }

  onQuickAdd(u: TenantUser): void {
    this.addingMember.set(true);
    this.memberError.set(null);
    this.teamService.addMember(this.teamId, { userId: u.id, role: 'MEMBER' }).subscribe({
      next: () => {
        this.addingMember.set(false);
        const current = this.team();
        if (current) {
          const newMember: import('../../../core/models/team.model').TeamMember = {
            userId: u.id,
            role: 'MEMBER',
            joinedAt: new Date().toISOString(),
            firstName: u.firstName,
            lastName:  u.lastName,
            email:     u.email,
          };
          this.team.set({ ...current, members: [...current.members, newMember] });
        }
        this.loadTeam(this.teamId, true);
      },
      error: (err: { status?: number }) => {
        if (err?.status === 409) {
          this.memberError.set(this.translocoService.translate('teams.detail.alreadyMember'));
        } else {
          this.memberError.set(this.translocoService.translate('teams.detail.inviteError'));
        }
        this.addingMember.set(false);
      },
    });
  }

  // ── Role change ───────────────────────────────────────────────────────────
  onRoleChange(member: TeamMember, newRole: TeamRole): void {
    // Guard: ignore if same role or already saving this member
    if (newRole === member.role || this.changingRoleFor() === member.userId) return;

    this.changingRoleFor.set(member.userId);
    const req: UpdateMemberRoleRequest = { role: newRole };
    this.teamService.updateMemberRole(this.teamId, member.userId, req).subscribe({
      next: () => {
        this.changingRoleFor.set(null);
        this.loadTeam(this.teamId, true);
      },
      error: () => {
        this.changingRoleFor.set(null);
        this.memberError.set(this.translocoService.translate('teams.detail.changeRoleError'));
      },
    });
  }

  // ── Member management ─────────────────────────────────────────────────────
  /** Display name from the enriched member fields the backend now returns */
  memberDisplayName(m: TeamMember): string {
    const name = [m.firstName, m.lastName].filter(Boolean).join(' ');
    return name || m.email || m.userId;
  }

  memberInitial(m: TeamMember): string {
    if (m.firstName) return m.firstName.charAt(0).toUpperCase();
    if (m.email)     return m.email.charAt(0).toUpperCase();
    return m.userId.charAt(0).toUpperCase();
  }

  onAddMember(): void {
    if (this.addMemberForm.invalid) { this.addMemberForm.markAllAsTouched(); return; }

    this.memberError.set(null);
    this.inviteSuccess.set(null);

    const userId   = this.resolveUserIdFromEmail();
    const emailVal = this.addMemberForm.controls.email.value?.trim() ?? '';

    if (userId) {
      // ── Known tenant user → existing add-member flow ──────────────────
      this.addingMember.set(true);
      const { role } = this.addMemberForm.getRawValue();

      this.teamService.addMember(this.teamId, { userId, role }).subscribe({
        next: () => {
          this.addingMember.set(false);
          this.addMemberForm.reset({ email: '', role: 'MEMBER' });
          // Optimistic update: add the new member to the signal immediately
          // so the list updates before the background GET completes.
          const current = this.team();
          if (current) {
            const resolved = this.tenantUsers().find(u => u.id === userId);
            const newMember: TeamMember = {
              userId,
              role,
              joinedAt: new Date().toISOString(),
              firstName: resolved?.firstName ?? null,
              lastName:  resolved?.lastName  ?? null,
              email:     resolved?.email     ?? emailVal,
            };
            this.team.set({ ...current, members: [...current.members, newMember] });
          }
          // Background refresh to get server-confirmed state
          this.loadTeam(this.teamId, true);
        },
        error: (err: { status?: number }) => {
          if (err?.status === 409) {
            this.memberError.set(this.translocoService.translate('teams.detail.alreadyMember'));
          } else if (err?.status === 403) {
            this.memberError.set(this.translocoService.translate('teams.detail.noPermission'));
          } else {
            this.memberError.set(this.translocoService.translate('teams.detail.inviteError'));
          }
          this.addingMember.set(false);
        },
      });
    } else {
      // ── Unknown email → invitation flow ──────────────────────────────
      this.addingMember.set(true);

      this.invitationService.inviteMember(this.teamId, emailVal).subscribe({
        next: () => {
          this.addingMember.set(false);
          this.addMemberForm.reset({ email: '', role: 'MEMBER' });
          this.inviteSuccess.set(`Invitation sent to ${emailVal}`);
        },
        error: (err: { status?: number }) => {
          if (err?.status === 409) {
            this.memberError.set('An invitation is already pending for this email');
          } else if (err?.status === 403) {
            this.memberError.set('Only admins can invite new members');
          } else {
            this.memberError.set(this.translocoService.translate('teams.detail.inviteError'));
          }
          this.addingMember.set(false);
        },
      });
    }
  }

  onRemoveMember(userId: string): void {
    this.confirmDialog.open({
      title: this.translocoService.translate('teams.detail.removeMember'),
      message: this.translocoService.translate('teams.detail.removeMemberMsg'),
      confirmLabel: this.translocoService.translate('teams.detail.removeMember'),
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.removingMember.set(userId);
      this.memberError.set(null);
      this.teamService.removeMember(this.teamId, userId).subscribe({
        next: () => {
          this.removingMember.set(null);
          this.loadTeam(this.teamId, true);
        },
          error: () => {
            this.memberError.set(this.translocoService.translate('teams.detail.deleteError'));
            this.removingMember.set(null);
          },
      });
    });
  }

  onDeleteTeam(): void {
    this.confirmDialog.open({
      title: this.translocoService.translate('teams.detail.deleteTeam'),
      message: this.translocoService.translate('teams.detail.deleteTeamMsg'),
      confirmLabel: this.translocoService.translate('teams.detail.deleteTeam'),
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.deleting.set(true);
      this.teamService.delete(this.teamId).subscribe({
        next:  () => { this.deleting.set(false); this.router.navigate(['/teams']); },
        error: () => { this.error.set(this.translocoService.translate('teams.detail.deleteError')); this.deleting.set(false); },
      });
    });
  }
}
