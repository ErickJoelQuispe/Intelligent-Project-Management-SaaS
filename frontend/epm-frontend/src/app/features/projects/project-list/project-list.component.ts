import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { ProjectStatus } from '../../../core/models/project.model';
import { ProjectService } from '../project.service';
import { Project } from '../../../core/models/project.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ProjectCardComponent } from '../../../shared/components/project-card/project-card.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-project-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslocoPipe,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    EmptyStateComponent,
    ProjectCardComponent,
  ],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  private readonly projectService   = inject(ProjectService);
  private readonly router           = inject(Router);
  private readonly confirmDialog    = inject(ConfirmDialogService);
  private readonly translocoService = inject(TranslocoService);

  allProjects     = signal<Project[]>([]);
  loading         = signal(false);
  error           = signal<string | null>(null);
  showArchived    = signal(false);

  projects = computed(() =>
    this.showArchived()
      ? this.allProjects()
      : this.allProjects().filter(p => p.status !== ProjectStatus.ARCHIVED)
  );

  activeCount = computed(() =>
    this.allProjects().filter(p => p.status === ProjectStatus.ACTIVE).length
  );
  archivedCount = computed(() =>
    this.allProjects().filter(p => p.status === ProjectStatus.ARCHIVED).length
  );

  ngOnInit(): void {
    this.loadProjects();
  }

  loadProjects(): void {
    this.loading.set(true);
    this.error.set(null);
    this.projectService.list(true).subscribe({
      next:  (p) => { this.allProjects.set(p); this.loading.set(false); },
      error: ()  => { this.error.set(this.translocoService.translate('projects.list.loadError')); this.loading.set(false); },
    });
  }

  toggleArchived(): void {
    this.showArchived.update(v => !v);
  }

  goToCreate(): void {
    this.router.navigate(['/projects/new']);
  }

  onProjectArchived(project: Project): void {
    this.confirmDialog.open({
      title: this.translocoService.translate('projects.archive.confirmTitle', { name: project.name }),
      message: this.translocoService.translate('projects.archive.confirmMessage'),
      confirmLabel: this.translocoService.translate('projects.archive.confirmBtn'),
      variant: 'warning',
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.projectService.archive(project.id).subscribe({
        next:  () => this.loadProjects(),
        error: () => this.error.set(this.translocoService.translate('projects.list.archiveError')),
      });
    });
  }

  onProjectRestored(project: Project): void {
    this.projectService.restore(project.id).subscribe({
      next:  () => this.loadProjects(),
      error: () => this.error.set(this.translocoService.translate('projects.list.restoreError')),
    });
  }
}
