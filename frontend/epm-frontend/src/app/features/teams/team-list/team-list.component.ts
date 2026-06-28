import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { TeamService } from '../team.service';
import { Team } from '../../../core/models/team.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';

@Component({
  selector: 'app-team-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    TranslocoPipe,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    EmptyStateComponent,
  ],
  templateUrl: './team-list.component.html',
  styleUrl: './team-list.component.scss',
})
export class TeamListComponent implements OnInit {
  private readonly teamService        = inject(TeamService);
  private readonly router             = inject(Router);
  private readonly translocoService   = inject(TranslocoService);

  teams   = signal<Team[]>([]);
  loading = signal(false);
  error   = signal<string | null>(null);

  totalMembers = computed(() =>
    this.teams().reduce((sum, t) => sum + t.members.length, 0)
  );

  ngOnInit(): void {
    this.loadTeams();
  }

  loadTeams(): void {
    this.loading.set(true);
    this.error.set(null);
    this.teamService.getAll().subscribe({
      next:  (t) => { this.teams.set(t); this.loading.set(false); },
      error: ()  => { this.error.set(this.translocoService.translate('teams.list.loadError')); this.loading.set(false); },
    });
  }

  goToCreate(): void {
    this.router.navigate(['/teams/new']);
  }
}
