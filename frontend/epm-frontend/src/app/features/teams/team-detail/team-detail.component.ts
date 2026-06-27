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
import { TeamService } from '../team.service';
import { UserService } from '../../settings/services/user.service';
import {
  Team,
  TeamMember,
  TeamRole,
  UpdateTeamRequest,
  UpdateMemberRoleRequest,
} from '../../../core/models/team.model';
import { TenantUser } from '../../../core/models/user-profile.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { TeamRoleBadgeComponent } from '../../../shared/components/team-role-badge/team-role-badge.component';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';

@Component({
  selector: 'app-team-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    FormsModule,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    TeamRoleBadgeComponent,
    AvatarComponent,
  ],
  templateUrl: './team-detail.component.html',
})
export class TeamDetailComponent {
  private readonly route       = inject(ActivatedRoute);
  private readonly router      = inject(Router);
  private readonly teamService = inject(TeamService);
  private readonly userService = inject(UserService);
  private readonly oauth       = inject(OAuthService);
  private readonly fb          = inject(FormBuilder);

  // ── Core state ──────────────────────────────────────────────────────────
  readonly team           = signal<Team | null>(null);
  readonly tenantUsers    = signal<TenantUser[]>([]);
  readonly loading        = signal(true);
  readonly error          = signal<string | null>(null);
  readonly addingMember   = signal(false);
  readonly removingMember = signal<string | null>(null);
  readonly deleting       = signal(false);
  readonly memberError    = signal<string | null>(null);

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

  /** Resolve email typed in the form to a userId from the tenant user list */
  readonly resolvedUserId = computed<string | null>(() => {
    const email = this.addMemberForm.controls.email.value?.trim().toLowerCase();
    if (!email) return null;
    const found = this.tenantUsers().find(u => u.email.toLowerCase() === email);
    return found?.id ?? null;
  });

  constructor() {
    const teamId = this.route.snapshot.paramMap.get('teamId');
    if (!teamId) {
      this.error.set('Team ID not found.');
      this.loading.set(false);
      return;
    }
    this.loadTeam(teamId);
    this.userService.listTenantUsers().subscribe({
      next: users => this.tenantUsers.set(users),
      error: () => { /* non-critical — only needed for add-member resolution */ },
    });
  }

  private loadTeam(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.teamService.getById(id).subscribe({
      next:  t  => { this.team.set(t); this.loading.set(false); },
      error: () => { this.error.set('Failed to load team.'); this.loading.set(false); },
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
        this.inlineSaveError.set('Could not save. Please try again.');
        this.isSaving = false;
        this.savingInline.set(false);
      },
    });
  }

  // ── Role change ───────────────────────────────────────────────────────────
  onRoleChange(member: TeamMember, newRole: TeamRole): void {
    this.changingRoleFor.set(member.userId);
    const req: UpdateMemberRoleRequest = { role: newRole };
    this.teamService.updateMemberRole(this.teamId, member.userId, req).subscribe({
      next: () => {
        this.changingRoleFor.set(null);
        this.loadTeam(this.teamId);
      },
      error: () => {
        this.changingRoleFor.set(null);
        this.memberError.set('Failed to change role. Please try again.');
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

    const userId = this.resolvedUserId();
    if (!userId) {
      this.memberError.set('No user found with that email address.');
      return;
    }

    this.addingMember.set(true);
    this.memberError.set(null);
    const { role } = this.addMemberForm.getRawValue();

    this.teamService.addMember(this.teamId, { userId, role }).subscribe({
      next: () => {
        this.addingMember.set(false);
        this.addMemberForm.reset({ email: '', role: 'MEMBER' });
        this.loadTeam(this.teamId);
      },
      error: () => {
        this.memberError.set('Failed to add member. Please try again.');
        this.addingMember.set(false);
      },
    });
  }

  onRemoveMember(userId: string): void {
    if (!confirm('Remove this member from the team?')) return;
    this.removingMember.set(userId);
    this.memberError.set(null);

    this.teamService.removeMember(this.teamId, userId).subscribe({
      next: () => {
        this.removingMember.set(null);
        this.loadTeam(this.teamId);
      },
      error: () => {
        this.memberError.set('Failed to remove member. Please try again.');
        this.removingMember.set(null);
      },
    });
  }

  onDeleteTeam(): void {
    if (!confirm('Delete this team? This action cannot be undone.')) return;
    this.deleting.set(true);

    this.teamService.delete(this.teamId).subscribe({
      next:  () => { this.deleting.set(false); this.router.navigate(['/teams']); },
      error: () => { this.error.set('Failed to delete team. Please try again.'); this.deleting.set(false); },
    });
  }
}
