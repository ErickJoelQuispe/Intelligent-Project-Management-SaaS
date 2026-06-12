import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { ProjectStatus } from '../../../core/models/project.model';
import { ProjectService } from '../project.service';
import { Project } from '../../../core/models/project.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ProjectCardComponent } from '../../../shared/components/project-card/project-card.component';

@Component({
  selector: 'app-project-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);

  projects        = signal<Project[]>([]);
  loading         = signal(false);
  error           = signal<string | null>(null);
  showArchived    = signal(false);

  activeCount = computed(() =>
    this.projects().filter(p => p.status === ProjectStatus.ACTIVE).length
  );
  archivedCount = computed(() =>
    this.projects().filter(p => p.status === ProjectStatus.ARCHIVED).length
  );

  ngOnInit(): void {
    this.loadProjects();
  }

  loadProjects(): void {
    this.loading.set(true);
    this.error.set(null);
    this.projectService.list(this.showArchived()).subscribe({
      next:  (p) => { this.projects.set(p); this.loading.set(false); },
      error: ()  => { this.error.set('Failed to load projects.'); this.loading.set(false); },
    });
  }

  toggleArchived(): void {
    this.showArchived.update(v => !v);
    this.loadProjects();
  }

  goToCreate(): void {
    this.router.navigate(['/projects/new']);
  }

  onProjectArchived(project: Project): void {
    if (!confirm(`Archive "${project.name}"? It will be hidden from the project list.`)) return;
    this.projectService.archive(project.id).subscribe({
      next:  () => this.loadProjects(),
      error: () => this.error.set('Failed to archive project. Please try again.'),
    });
  }
}
