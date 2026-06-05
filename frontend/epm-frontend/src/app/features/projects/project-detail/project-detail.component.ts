import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProjectService } from '../project.service';
import { AiService, TaskDraft } from '../../ai/ai.service';
import { Project } from '../../../core/models/project.model';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="detail-container">
      <div class="header">
        <a mat-button routerLink="/projects" class="back-link">&larr; Back to Projects</a>
      </div>

      @if (loading()) {
        <div class="loading">
          <mat-spinner diameter="40" />
        </div>
      } @else if (error()) {
        <mat-card class="error-card">
          <p>{{ error() }}</p>
        </mat-card>
      } @else if (project(); as p) {
        <mat-card class="project-card">
          <mat-card-header>
            <mat-card-title>{{ p.name }}</mat-card-title>
            <mat-card-subtitle>Status: {{ p.status }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p *ngIf="p.description">{{ p.description }}</p>
            <p class="meta">Created: {{ p.createdAt | date }}</p>
          </mat-card-content>
          <mat-card-actions>
            <a mat-raised-button color="primary" [routerLink]="['/projects', p.id, 'tasks']">
              View Tasks
            </a>
            <a mat-raised-button [routerLink]="['/projects', p.id, 'tasks', 'kanban']">
              Kanban Board
            </a>
          </mat-card-actions>
        </mat-card>

        <mat-card class="ai-card">
          <mat-card-header>
            <mat-card-title>AI Assistant</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="ai-section">
              <h3>Generate Tasks</h3>
              <p class="hint">
                Let AI suggest tasks based on this project's description.
              </p>
              <button
                mat-raised-button
                color="accent"
                (click)="generateTasks()"
                [disabled]="generating()"
              >
                {{ generating() ? 'Generating...' : 'Generate Tasks with AI' }}
              </button>

              @if (draftTasks().length > 0) {
                <div class="draft-list">
                  <h4>Suggested Tasks</h4>
                  @for (task of draftTasks(); track task.title) {
                    <div class="draft-item">
                      <span class="priority-badge" [class]="task.priority.toLowerCase()">
                        {{ task.priority }}
                      </span>
                      <strong>{{ task.title }}</strong>
                      <p>{{ task.description }}</p>
                    </div>
                  }
                </div>
              }
            </div>

            <div class="ai-section">
              <h3>Project Summary</h3>
              <button
                mat-stroked-button
                (click)="summarizeProject()"
                [disabled]="summarizing()"
              >
                {{ summarizing() ? 'Summarizing...' : 'Summarize Project' }}
              </button>

              @if (summary()) {
                <div class="summary-box">
                  <p>{{ summary() }}</p>
                </div>
              }
            </div>

            @if (aiError()) {
              <p class="error-msg">{{ aiError() }}</p>
            }
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [
    `
      .detail-container {
        max-width: 800px;
        margin: 2rem auto;
        padding: 0 1rem;
      }
      .header { margin-bottom: 1rem; }
      .back-link { text-decoration: none; color: #1976d2; }
      .loading, .error-card { display: flex; justify-content: center; padding: 2rem; }
      .project-card, .ai-card { margin-bottom: 1.5rem; }
      .meta { color: #666; font-size: 0.85rem; }
      .ai-card mat-card-content { display: flex; flex-direction: column; gap: 1.5rem; }
      .ai-section { border-top: 1px solid #eee; padding-top: 1rem; }
      .ai-section:first-child { border-top: none; padding-top: 0; }
      .hint { color: #666; font-size: 0.9rem; margin-bottom: 0.5rem; }
      .draft-list { margin-top: 1rem; }
      .draft-item {
        padding: 0.75rem;
        margin-bottom: 0.5rem;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        background: #fafafa;
      }
      .priority-badge {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 0.75rem;
        font-weight: bold;
        margin-right: 0.5rem;
      }
      .priority-badge.high { background: #ffcdd2; color: #c62828; }
      .priority-badge.medium { background: #fff9c4; color: #f57f17; }
      .priority-badge.low { background: #c8e6c9; color: #2e7d32; }
      .summary-box {
        margin-top: 1rem;
        padding: 1rem;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        background: #f5f5f5;
        line-height: 1.6;
      }
      .error-msg { color: #c62828; font-size: 0.9rem; }
    `,
  ],
})
export class ProjectDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly aiService = inject(AiService);

  readonly project = signal<Project | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly generating = signal(false);
  readonly summarizing = signal(false);
  readonly draftTasks = signal<TaskDraft[]>([]);
  readonly summary = signal<string | null>(null);
  readonly aiError = signal<string | null>(null);

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
      next: (p) => {
        this.project.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load project.');
        this.loading.set(false);
      },
    });
  }

  generateTasks(): void {
    const project = this.project();
    if (!project) return;

    this.generating.set(true);
    this.aiError.set(null);
    this.draftTasks.set([]);

    this.aiService
      .generateTasks(project.id, project.description ?? project.name)
      .subscribe({
        next: (res) => {
          this.draftTasks.set(res.tasks);
          this.generating.set(false);
        },
        error: (err) => {
          this.aiError.set(
            err.status === 503
              ? 'AI service is temporarily unavailable. Please try again later.'
              : 'Failed to generate tasks. Please try again.',
          );
          this.generating.set(false);
        },
      });
  }

  summarizeProject(): void {
    const project = this.project();
    if (!project) return;

    this.summarizing.set(true);
    this.aiError.set(null);
    this.summary.set(null);

    this.aiService.getProjectSummary(project.id).subscribe({
      next: (res) => {
        this.summary.set(res.summary);
        this.summarizing.set(false);
      },
      error: () => {
        this.aiError.set('Failed to summarize project. Please try again.');
        this.summarizing.set(false);
      },
    });
  }
}
