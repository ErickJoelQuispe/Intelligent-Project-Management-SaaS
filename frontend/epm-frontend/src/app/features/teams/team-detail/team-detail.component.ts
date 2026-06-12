import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { SlicePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { OAuthService } from 'angular-oauth2-oidc';
import { TeamService } from '../team.service';
import { Team, TeamRole } from '../../../core/models/team.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { CardComponent } from '../../../shared/components/card/card.component';
import { TeamRoleBadgeComponent } from '../../../shared/components/team-role-badge/team-role-badge.component';

@Component({
  selector: 'app-team-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SlicePipe,
    RouterLink,
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    CardComponent,
    TeamRoleBadgeComponent,
  ],
  templateUrl: './team-detail.component.html',
})
export class TeamDetailComponent {
  private readonly route       = inject(ActivatedRoute);
  private readonly router      = inject(Router);
  private readonly teamService = inject(TeamService);
  private readonly oauth       = inject(OAuthService);
  private readonly fb          = inject(FormBuilder);

  readonly team        = signal<Team | null>(null);
  readonly loading     = signal(true);
  readonly error       = signal<string | null>(null);
  readonly addingMember   = signal(false);
  readonly removingMember = signal<string | null>(null);
  readonly deleting    = signal(false);
  readonly memberError = signal<string | null>(null);

  readonly roleOptions: TeamRole[] = ['OWNER', 'MEMBER', 'VIEWER'];

  readonly addMemberForm = this.fb.nonNullable.group({
    userId: ['', Validators.required],
    role:   ['MEMBER' as TeamRole, Validators.required],
  });

  readonly isOwner = computed(() => {
    const t = this.team();
    if (!t) return false;
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    const sub = claims?.['sub'] ?? '';
    return t.ownerId === sub;
  });

  constructor() {
    const teamId = this.route.snapshot.paramMap.get('teamId');
    if (!teamId) {
      this.error.set('Team ID not found.');
      this.loading.set(false);
      return;
    }
    this.loadTeam(teamId);
  }

  private loadTeam(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.teamService.getById(id).subscribe({
      next:  (t) => { this.team.set(t); this.loading.set(false); },
      error: ()  => { this.error.set('Failed to load team.'); this.loading.set(false); },
    });
  }

  private get teamId(): string {
    return this.team()?.id ?? this.route.snapshot.paramMap.get('teamId') ?? '';
  }

  onAddMember(): void {
    if (this.addMemberForm.invalid) { this.addMemberForm.markAllAsTouched(); return; }

    this.addingMember.set(true);
    this.memberError.set(null);
    const { userId, role } = this.addMemberForm.getRawValue();

    this.teamService.addMember(this.teamId, { userId, role }).subscribe({
      next: () => {
        this.addingMember.set(false);
        this.addMemberForm.reset({ userId: '', role: 'MEMBER' });
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
