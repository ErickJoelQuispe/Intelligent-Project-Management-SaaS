import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TaskService } from '../task.service';
import { Task } from '../../../core/models/task.models';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { TaskStatusBadgeComponent } from '../../../shared/components/task-status-badge/task-status-badge.component';
import { TaskPriorityBadgeComponent } from '../../../shared/components/task-priority-badge/task-priority-badge.component';

@Component({
  selector: 'app-task-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    EmptyStateComponent,
    TaskStatusBadgeComponent,
    TaskPriorityBadgeComponent,
  ],
  template: `
    <div class="task-panel-content">

      @if (loading()) {
        <app-spinner size="lg" [full]="true" label="Loading tasks..." />

      } @else if (error()) {
        <app-error-banner
          [message]="error()!"
          retryLabel="Try again"
          (retry)="loadTasks()"
        />

      } @else if (tasks().length === 0) {
        <app-empty-state
          icon="task"
          title="No tasks yet"
          description="Create the first task for this project."
        >
          <app-button action variant="primary"
                      [routerLink]="['/projects', projectId(), 'tasks', 'new']">
            <span class="material-symbols-rounded" aria-hidden="true">add</span>
            New task
          </app-button>
        </app-empty-state>

      } @else {

        <div class="task-table" role="table" aria-label="Task list">

          <!-- Table header -->
          <div class="task-table-head" role="row">
            <span class="task-th task-th--title" role="columnheader">Title</span>
            <span class="task-th" role="columnheader">Status</span>
            <span class="task-th" role="columnheader">Priority</span>
            <span class="task-th task-th--mono" role="columnheader">Deadline</span>
            <span class="task-th task-th--actions" role="columnheader" aria-label="Actions"></span>
          </div>

          <!-- Rows -->
          @for (task of tasks(); track task.id; let i = $index) {
            <div
              class="task-row animate-card-in"
              [class]="'stagger-' + ((i % 6) + 1)"
              [class.task-row--odd]="i % 2 !== 0"
              role="row"
              [attr.aria-label]="task.title"
            >

              <!-- Priority accent bar -->
              <div class="task-row-priority-bar" [attr.data-priority]="task.priority" aria-hidden="true"></div>

              <!-- Title -->
              <div class="task-row-title" role="cell">
                <span class="task-title-text">{{ task.title }}</span>
                @if (task.parentTaskId) {
                  <span class="task-subtask-chip" aria-label="Subtask">
                    <span class="material-symbols-rounded" aria-hidden="true">subdirectory_arrow_right</span>
                    Subtask
                  </span>
                }
              </div>

              <!-- Status -->
              <div role="cell">
                <app-task-status-badge [status]="task.status" />
              </div>

              <!-- Priority -->
              <div role="cell">
                <app-task-priority-badge [priority]="task.priority" />
              </div>

              <!-- Deadline -->
              <div class="task-row-deadline" role="cell">
                @if (task.deadline) {
                  <span
                    class="task-deadline-chip"
                    [class.task-deadline-chip--overdue]="isOverdue(task.deadline)"
                  >
                    <span class="material-symbols-rounded" aria-hidden="true">schedule</span>
                    {{ task.deadline | date:'MMM d, yy' }}
                  </span>
                } @else {
                  <span class="task-deadline-empty" aria-label="No deadline">—</span>
                }
              </div>

              <!-- Actions -->
              <div class="task-row-actions" role="cell">
                <app-button variant="ghost" size="sm"
                            [routerLink]="['/projects', projectId(), 'tasks', task.id, 'edit']"
                            [attr.aria-label]="'Edit task: ' + task.title">
                  <span class="material-symbols-rounded" aria-hidden="true">edit</span>
                </app-button>
                <button
                  class="task-delete-btn"
                  (click)="deleteTask(task.id, task.title)"
                  [attr.aria-label]="'Delete task: ' + task.title"
                >
                  <span class="material-symbols-rounded" aria-hidden="true">delete</span>
                </button>
              </div>

            </div>
          }

        </div>

        <!-- Pagination -->
        @if (totalPages > 1) {
          <div class="task-pagination" aria-label="Pagination">
            <span class="task-pagination-info">
              Page {{ pageIndex + 1 }} of {{ totalPages }}
              <span class="task-pagination-total">&nbsp;· {{ totalElements() }} tasks</span>
            </span>
            <div class="task-pagination-controls">
              <app-button variant="secondary" size="sm"
                          [disabled]="pageIndex === 0"
                          (click)="onPageChange(pageIndex - 1)"
                          aria-label="Previous page">
                <span class="material-symbols-rounded" aria-hidden="true">arrow_back</span>
                Prev
              </app-button>
              <app-button variant="secondary" size="sm"
                          [disabled]="pageIndex + 1 >= totalPages"
                          (click)="onPageChange(pageIndex + 1)"
                          aria-label="Next page">
                Next
                <span class="material-symbols-rounded" aria-hidden="true">arrow_forward</span>
              </app-button>
            </div>
          </div>
        }

      }

    </div>

    <style>
      /* ── Panel content wrapper ─────────────────── */
      .task-panel-content {
        padding: 1.25rem 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
        height: 100%;
        box-sizing: border-box;
      }

      /* ── Table shell ───────────────────────────── */
      .task-table {
        border-radius: 0.875rem;
        overflow: hidden;
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-md);
      }

      /* ── Table header ──────────────────────────── */
      .task-table-head {
        display: grid;
        grid-template-columns: 2fr 1fr 1fr 1fr auto;
        align-items: center;
        padding: 0 1.25rem;
        height: 2.75rem;
        background: color-mix(in oklch, var(--color-bg-elevated) 90%, transparent);
        border-bottom: 1px solid var(--color-border);
      }

      .task-th {
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.07em;
        text-transform: uppercase;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
      }
      .task-th--title { padding-left: 0.75rem; }
      .task-th--mono  { font-family: 'JetBrains Mono', monospace; }
      .task-th--actions { min-width: 5rem; }

      /* ── Table row ─────────────────────────────── */
      .task-row {
        position: relative;
        display: grid;
        grid-template-columns: 2fr 1fr 1fr 1fr auto;
        align-items: center;
        padding: 0 1.25rem;
        min-height: 3.25rem;
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 45%, transparent);
        background: var(--color-bg-base);
        transition: background 0.12s ease;
      }
      .task-row:last-child { border-bottom: none; }
      .task-row--odd { background: var(--color-bg-surface); }
      .task-row:hover {
        background: color-mix(in oklch, var(--color-accent) 4%, var(--color-bg-surface));
      }
      .task-row:hover .task-row-priority-bar { width: 3.5px; }
      .task-row:hover .task-delete-btn       { opacity: 1; }
      .task-row:hover .task-title-text       { color: var(--color-accent); }

      /* ── Priority accent bar ───────────────────── */
      .task-row-priority-bar {
        position: absolute;
        left: 0;
        top: 0.625rem;
        bottom: 0.625rem;
        width: 3px;
        border-radius: 0 2px 2px 0;
        transition: width 0.12s ease;
      }
      .task-row-priority-bar[data-priority="HIGH"]   { background: var(--color-danger); }
      .task-row-priority-bar[data-priority="MEDIUM"] { background: var(--color-warning); }
      .task-row-priority-bar[data-priority="LOW"]    { background: var(--color-info); }

      /* ── Title cell ────────────────────────────── */
      .task-row-title {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding-left: 0.75rem;
        min-width: 0;
      }

      .task-title-text {
        font-size: 0.875rem;
        font-weight: 500;
        color: var(--color-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        transition: color 0.12s ease;
        letter-spacing: 0.005em;
      }

      .task-subtask-chip {
        display: inline-flex;
        align-items: center;
        gap: 0.2rem;
        padding: 0.125rem 0.4rem;
        border-radius: 9999px;
        font-size: 0.625rem;
        font-weight: 600;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--color-text-muted);
        background: color-mix(in oklch, var(--color-bg-overlay) 70%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        flex-shrink: 0;
      }
      .task-subtask-chip .material-symbols-rounded { font-size: 0.75rem; }

      /* ── Deadline cell ─────────────────────────── */
      .task-row-deadline {
        display: flex;
        align-items: center;
      }

      .task-deadline-chip {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.75rem;
        font-family: 'JetBrains Mono', monospace;
        font-feature-settings: 'tnum';
        color: var(--color-text-muted);
      }
      .task-deadline-chip .material-symbols-rounded { font-size: 0.8125rem; }
      .task-deadline-chip--overdue {
        color: var(--color-danger);
        font-weight: 600;
      }

      .task-deadline-empty {
        font-size: 0.875rem;
        color: var(--color-text-disabled);
        font-family: 'JetBrains Mono', monospace;
      }

      /* ── Actions cell ──────────────────────────── */
      .task-row-actions {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        justify-content: flex-end;
        min-width: 5rem;
      }

      .task-delete-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.375rem;
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--color-text-disabled);
        opacity: 0;
        transition: opacity 0.12s ease, background 0.12s ease, color 0.12s ease;
        padding: 0;
      }
      .task-delete-btn:hover {
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
        color: var(--color-danger);
        opacity: 1;
      }
      .task-delete-btn:focus-visible {
        outline: 2px solid var(--color-danger);
        outline-offset: 1px;
        opacity: 1;
      }
      .task-delete-btn .material-symbols-rounded { font-size: 1rem; }

      /* ── Pagination ────────────────────────────── */
      .task-pagination {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 0.25rem;
      }

      .task-pagination-info {
        font-size: 0.75rem;
        font-family: 'JetBrains Mono', monospace;
        color: var(--color-text-muted);
      }

      .task-pagination-total {
        color: var(--color-text-disabled);
      }

      .task-pagination-controls {
        display: flex;
        gap: 0.5rem;
      }
    </style>
  `,
})
export class TaskPanelComponent implements OnInit {
  private readonly taskService = inject(TaskService);

  projectId = input.required<string>();

  tasks         = signal<Task[]>([]);
  loading       = signal(false);
  error         = signal<string | null>(null);
  totalElements = signal(0);
  pageSize      = 10;
  pageIndex     = 0;

  ngOnInit(): void {
    this.loadTasks();
  }

  loadTasks(): void {
    this.loading.set(true);
    this.error.set(null);
    this.taskService.list(this.projectId(), this.pageIndex, this.pageSize).subscribe({
      next:  (page) => { this.tasks.set(page.content); this.totalElements.set(page.totalElements); this.loading.set(false); },
      error: ()     => { this.error.set('Failed to load tasks.'); this.loading.set(false); },
    });
  }

  deleteTask(taskId: string, title: string): void {
    if (!confirm(`Delete "${title}"? This cannot be undone.`)) return;
    this.taskService.delete(taskId).subscribe({
      next:  () => this.loadTasks(),
      error: () => this.error.set('Failed to delete task.'),
    });
  }

  onPageChange(page: number): void {
    this.pageIndex = page;
    this.loadTasks();
  }

  get totalPages(): number {
    return Math.ceil(this.totalElements() / this.pageSize);
  }

  isOverdue(deadline: string): boolean {
    return new Date(deadline) < new Date();
  }
}
