import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ProjectService } from '../project.service';
import { AiService, TaskDraft } from '../../ai/ai.service';
import { TaskService } from '../../tasks/task.service';
import { Project } from '../../../core/models/project.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { ProjectCardComponent } from '../../../shared/components/project-card/project-card.component';
import { CardComponent } from '../../../shared/components/card/card.component';
import { AiDraftTaskItemComponent } from '../../../shared/components/ai-draft-task-item/ai-draft-task-item.component';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    ProjectCardComponent,
    CardComponent,
    AiDraftTaskItemComponent,
  ],
  templateUrl: './project-detail.component.html',
})
export class ProjectDetailComponent {
  private readonly route          = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly aiService      = inject(AiService);
  private readonly taskService    = inject(TaskService);

  readonly project     = signal<Project | null>(null);
  readonly loading     = signal(true);
  readonly error       = signal<string | null>(null);
  readonly generating  = signal(false);
  readonly summarizing = signal(false);
  readonly savingTasks = signal(false);
  readonly saveSuccess = signal(false);
  readonly draftTasks  = signal<TaskDraft[]>([]);
  readonly summary     = signal<string | null>(null);
  readonly aiError     = signal<string | null>(null);

  constructor() {
    const projectId = this.route.snapshot.paramMap.get('projectId');
    if (!projectId) {
      this.error.set('Project ID not found.');
      this.loading.set(false);
      return;
    }
    this.loadProject(projectId);
  }

  private loadProject(id: string): void {
    this.projectService.getById(id).subscribe({
      next:  (p) => { this.project.set(p); this.loading.set(false); },
      error: ()  => { this.error.set('Failed to load project.'); this.loading.set(false); },
    });
  }

  generateTasks(): void {
    const p = this.project();
    if (!p) return;
    this.generating.set(true);
    this.aiError.set(null);
    this.draftTasks.set([]);
    this.saveSuccess.set(false);
    this.aiService.generateTasks(p.id, p.description ?? p.name).subscribe({
      next:  (res) => { this.draftTasks.set(res.tasks); this.generating.set(false); },
      error: (err) => {
        this.aiError.set(err.status === 503
          ? 'AI service is temporarily unavailable.'
          : 'Failed to generate tasks. Please try again.');
        this.generating.set(false);
      },
    });
  }

  saveAllTasks(): void {
    const p = this.project();
    const drafts = this.draftTasks();
    if (!p || drafts.length === 0) return;

    this.savingTasks.set(true);
    this.aiError.set(null);

    // Crear todas las tasks en paralelo con forkJoin
    const requests = drafts.map(draft =>
      this.taskService.create({
        projectId:   p.id,
        title:       draft.title,
        description: draft.description,
        priority:    draft.priority,
      })
    );

    forkJoin(requests).subscribe({
      next: () => {
        this.savingTasks.set(false);
        this.saveSuccess.set(true);
        // Limpiar drafts después de 2 segundos
        setTimeout(() => {
          this.draftTasks.set([]);
          this.saveSuccess.set(false);
        }, 2000);
      },
      error: () => {
        this.aiError.set('Failed to save some tasks. Please try again.');
        this.savingTasks.set(false);
      },
    });
  }

  summarizeProject(): void {
    const p = this.project();
    if (!p) return;
    this.summarizing.set(true);
    this.aiError.set(null);
    this.summary.set(null);
    this.aiService.getProjectSummary(p.id).subscribe({
      next:  (res) => { this.summary.set(res.summary); this.summarizing.set(false); },
      error: ()    => { this.aiError.set('Failed to summarize project.'); this.summarizing.set(false); },
    });
  }
}
