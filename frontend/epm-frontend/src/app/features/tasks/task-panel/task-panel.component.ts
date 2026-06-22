import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  input,
  signal,
  computed,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TaskService } from '../task.service';
import {
  Task,
  TaskStatus,
  TASK_STATUS_ORDER,
  TASK_STATUS_LABELS,
} from '../../../core/models/task.models';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { TaskStatusBadgeComponent } from '../../../shared/components/task-status-badge/task-status-badge.component';
import { TaskPriorityBadgeComponent } from '../../../shared/components/task-priority-badge/task-priority-badge.component';

interface StatusGroup {
  status: TaskStatus;
  label: string;
  tasks: Task[];
}

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

        @for (group of groupedTasks(); track group.status) {
          <div class="task-group">

            <!-- Group header -->
            <button
              class="task-group-header"
              (click)="toggleGroup(group.status)"
              [attr.aria-expanded]="!collapsedGroups().has(group.status)"
              [attr.aria-label]="group.label + ' — ' + group.tasks.length + ' tasks'"
            >
              <span class="task-group-label">{{ group.label }}</span>
              <span class="task-group-count">{{ group.tasks.length }}</span>
              <span class="task-group-chevron material-symbols-rounded"
                    [class.task-group-chevron--collapsed]="collapsedGroups().has(group.status)"
                    aria-hidden="true">
                expand_more
              </span>
            </button>

            <!-- Group body -->
            <div
              class="task-group-body"
              [class.task-group-body--collapsed]="collapsedGroups().has(group.status)"
            >
              @for (task of group.tasks; track task.id; let i = $index) {

                <div
                  class="task-row"
                  [class.task-row--expanded]="expandedTaskIds().has(task.id)"
                  [attr.data-priority]="task.priority"
                >
                  <!-- Clickable summary row -->
                  <div
                    class="task-row-summary"
                    (click)="toggleExpand(task.id)"
                    role="button"
                    tabindex="0"
                    (keydown.enter)="toggleExpand(task.id)"
                    (keydown.space)="toggleExpand(task.id)"
                    [attr.aria-expanded]="expandedTaskIds().has(task.id)"
                    [attr.aria-label]="task.title"
                  >
                    <!-- Priority accent bar -->
                    <div class="task-priority-bar" aria-hidden="true"></div>

                    <!-- Expand chevron -->
                    <span
                      class="task-expand-icon material-symbols-rounded"
                      [class.task-expand-icon--open]="expandedTaskIds().has(task.id)"
                      aria-hidden="true"
                    >chevron_right</span>

                    <!-- Title -->
                    <span class="task-title">{{ task.title }}</span>

                    <!-- Priority badge -->
                    <app-task-priority-badge [priority]="task.priority" />

                    <!-- Deadline -->
                    @if (task.deadline) {
                      <span
                        class="task-deadline"
                        [class.task-deadline--overdue]="isOverdue(task.deadline)"
                      >
                        <span class="material-symbols-rounded" aria-hidden="true">schedule</span>
                        {{ task.deadline | date:'MMM d' }}
                      </span>
                    } @else {
                      <span class="task-deadline-empty" aria-hidden="true">—</span>
                    }

                    <!-- Hover actions -->
                    <div class="task-row-actions" role="group" [attr.aria-label]="'Actions for ' + task.title">
                      <a
                        class="task-action-btn"
                        [routerLink]="['/projects', projectId(), 'tasks', task.id, 'edit']"
                        [attr.aria-label]="'Edit task: ' + task.title"
                        (click)="$event.stopPropagation()"
                      >
                        <span class="material-symbols-rounded" aria-hidden="true">edit</span>
                      </a>
                      <button
                        class="task-action-btn task-action-btn--danger"
                        (click)="deleteTask($event, task.id, task.title)"
                        [attr.aria-label]="'Delete task: ' + task.title"
                      >
                        <span class="material-symbols-rounded" aria-hidden="true">delete</span>
                      </button>
                    </div>
                  </div>

                  <!-- Expandable detail area -->
                  <div
                    class="task-row-detail"
                    [class.task-row-detail--open]="expandedTaskIds().has(task.id)"
                    role="region"
                    [attr.aria-label]="'Details for ' + task.title"
                  >
                    <div class="task-row-detail-inner">

                      <!-- Description -->
                      <div class="task-detail-description">
                        @if (task.description) {
                          <p class="task-description-text">{{ task.description }}</p>
                        } @else {
                          <p class="task-description-empty">No description</p>
                        }
                      </div>

                      <!-- Subtasks -->
                      <div class="task-subtasks-section">
                        <span class="task-subtasks-label">
                          <span class="material-symbols-rounded" aria-hidden="true">subdirectory_arrow_right</span>
                          Subtasks
                        </span>

                        @if (loadingSubtasks().has(task.id)) {
                          <div class="task-subtasks-loading">
                            <app-spinner size="sm" label="Loading subtasks..." />
                          </div>
                        } @else if (subtaskMap().get(task.id)?.length === 0) {
                          <p class="task-subtasks-empty">No subtasks</p>
                        } @else if (subtaskMap().get(task.id)) {
                          <div class="task-subtasks-list">
                            @for (sub of subtaskMap().get(task.id)!; track sub.id) {
                              <div class="task-subtask-row">
                                <span class="task-subtask-connector" aria-hidden="true"></span>
                                <span class="task-subtask-title">{{ sub.title }}</span>
                                <app-task-status-badge [status]="sub.status" />
                                <app-task-priority-badge [priority]="sub.priority" />
                              </div>
                            }
                          </div>
                        }
                      </div>

                    </div>
                  </div>

                </div>

              }
            </div>

          </div>
        }

      }

    </div>

    <style>
      /* ── Panel wrapper ─────────────────────────── */
      .task-panel-content {
        padding: 1rem 1.25rem 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 0.625rem;
      }

      /* ── Group ─────────────────────────────────── */
      .task-group {
        border-radius: 0.875rem;
        border: 1px solid var(--color-border);
        overflow: hidden;
        box-shadow: var(--shadow-sm);
      }

      /* ── Group header ──────────────────────────── */
      .task-group-header {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        width: 100%;
        padding: 0.625rem 1rem;
        background: color-mix(in oklch, var(--color-bg-elevated) 80%, transparent);
        border: none;
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 60%, transparent);
        cursor: pointer;
        text-align: left;
        transition: background 0.15s ease;
      }
      .task-group-header:hover {
        background: color-mix(in oklch, var(--color-bg-elevated) 95%, var(--color-accent));
      }

      .task-group-label {
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.07em;
        text-transform: uppercase;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
        flex: 1;
      }

      .task-group-count {
        font-size: 0.6875rem;
        font-weight: 700;
        font-family: 'JetBrains Mono', monospace;
        color: var(--color-text-disabled);
        background: color-mix(in oklch, var(--color-bg-overlay) 70%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        border-radius: 9999px;
        padding: 0.0625rem 0.4375rem;
        line-height: 1.4;
      }

      .task-group-chevron {
        font-size: 1rem;
        color: var(--color-text-disabled);
        transition: transform 0.2s ease;
      }
      .task-group-chevron--collapsed {
        transform: rotate(-90deg);
      }

      /* ── Group body (collapse) ─────────────────── */
      .task-group-body {
        max-height: 2000px;
        overflow: hidden;
        transition: max-height 0.3s ease;
      }
      .task-group-body--collapsed {
        max-height: 0;
      }

      /* ── Task row ──────────────────────────────── */
      .task-row {
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 40%, transparent);
        background: var(--color-bg-base);
      }
      .task-row:last-child { border-bottom: none; }
      .task-row--expanded { background: color-mix(in oklch, var(--color-bg-elevated) 50%, var(--color-bg-base)); }

      /* ── Task row summary (clickable header) ────── */
      .task-row-summary {
        position: relative;
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.625rem 1rem 0.625rem 0.75rem;
        cursor: pointer;
        transition: background 0.12s ease;
        user-select: none;
        outline: none;
      }
      .task-row-summary:hover {
        background: color-mix(in oklch, var(--color-accent) 4%, var(--color-bg-surface));
      }
      .task-row-summary:focus-visible {
        outline: 2px solid var(--color-accent);
        outline-offset: -2px;
      }
      .task-row-summary:hover .task-action-btn { opacity: 1; }
      .task-row-summary:hover .task-priority-bar { width: 3.5px; }

      /* ── Priority accent bar ───────────────────── */
      .task-priority-bar {
        position: absolute;
        left: 0;
        top: 0.5rem;
        bottom: 0.5rem;
        width: 3px;
        border-radius: 0 2px 2px 0;
        transition: width 0.12s ease;
      }
      .task-row[data-priority="HIGH"]    .task-priority-bar { background: var(--color-danger); }
      .task-row[data-priority="MEDIUM"]  .task-priority-bar { background: var(--color-warning); }
      .task-row[data-priority="LOW"]     .task-priority-bar { background: var(--color-info); }

      /* ── Expand chevron ────────────────────────── */
      .task-expand-icon {
        font-size: 1rem;
        color: var(--color-text-disabled);
        flex-shrink: 0;
        transition: transform 0.2s ease;
      }
      .task-expand-icon--open { transform: rotate(90deg); }

      /* ── Task title ────────────────────────────── */
      .task-title {
        flex: 1;
        font-size: 0.875rem;
        font-weight: 500;
        color: var(--color-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        letter-spacing: 0.005em;
        transition: color 0.12s ease;
      }
      .task-row-summary:hover .task-title { color: var(--color-accent); }

      /* ── Deadline ──────────────────────────────── */
      .task-deadline {
        display: inline-flex;
        align-items: center;
        gap: 0.2rem;
        font-size: 0.6875rem;
        font-family: 'JetBrains Mono', monospace;
        font-feature-settings: 'tnum';
        color: var(--color-text-muted);
        flex-shrink: 0;
      }
      .task-deadline .material-symbols-rounded { font-size: 0.75rem; }
      .task-deadline--overdue {
        color: var(--color-danger);
        font-weight: 600;
      }
      .task-deadline-empty {
        font-size: 0.875rem;
        color: var(--color-text-disabled);
        font-family: 'JetBrains Mono', monospace;
        flex-shrink: 0;
      }

      /* ── Row actions ───────────────────────────── */
      .task-row-actions {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex-shrink: 0;
      }

      .task-action-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.625rem;
        height: 1.625rem;
        border-radius: 0.375rem;
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--color-text-disabled);
        opacity: 0;
        transition: opacity 0.12s ease, background 0.12s ease, color 0.12s ease;
        padding: 0;
        text-decoration: none;
        font-size: inherit;
      }
      .task-action-btn:hover {
        background: color-mix(in oklch, var(--color-bg-overlay) 60%, transparent);
        color: var(--color-text-secondary);
        opacity: 1;
      }
      .task-action-btn--danger:hover {
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
        color: var(--color-danger);
      }
      .task-action-btn:focus-visible {
        outline: 2px solid var(--color-accent);
        outline-offset: 1px;
        opacity: 1;
      }
      .task-action-btn .material-symbols-rounded { font-size: 0.875rem; }

      /* ── Expandable detail panel ───────────────── */
      .task-row-detail {
        max-height: 0;
        overflow: hidden;
        transition: max-height 0.28s ease;
      }
      .task-row-detail--open {
        max-height: 2000px;
      }

      .task-row-detail-inner {
        padding: 0.75rem 1rem 0.875rem 2.5rem;
        background: color-mix(in oklch, var(--color-bg-elevated) 80%, transparent);
        border-top: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
      }

      /* ── Description ───────────────────────────── */
      .task-detail-description {
        margin: 0;
      }

      .task-description-text {
        font-size: 0.8125rem;
        color: var(--color-text-secondary);
        line-height: 1.6;
        margin: 0;
      }

      .task-description-empty {
        font-size: 0.8125rem;
        color: var(--color-text-disabled);
        font-style: italic;
        margin: 0;
      }

      /* ── Subtasks section ──────────────────────── */
      .task-subtasks-section {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      .task-subtasks-label {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
      }
      .task-subtasks-label .material-symbols-rounded { font-size: 0.875rem; }

      .task-subtasks-loading {
        padding: 0.25rem 0;
      }

      .task-subtasks-empty {
        font-size: 0.8125rem;
        color: var(--color-text-disabled);
        font-style: italic;
        margin: 0;
      }

      .task-subtasks-list {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }

      /* ── Subtask row ───────────────────────────── */
      .task-subtask-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.3125rem 0.625rem;
        border-radius: 0.375rem;
        background: color-mix(in oklch, var(--color-bg-base) 80%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        position: relative;
      }

      .task-subtask-connector {
        position: absolute;
        left: -1rem;
        top: 50%;
        width: 0.875rem;
        height: 1px;
        background: color-mix(in oklch, var(--color-border) 70%, transparent);
        display: block;
      }

      .task-subtask-title {
        flex: 1;
        font-size: 0.8125rem;
        color: var(--color-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    </style>
  `,
})
export class TaskPanelComponent implements OnInit {
  private readonly taskService = inject(TaskService);

  projectId = input.required<string>();

  tasks   = signal<Task[]>([]);
  loading = signal(false);
  error   = signal<string | null>(null);

  expandedTaskIds  = signal<Set<string>>(new Set());
  subtaskMap       = signal<Map<string, Task[]>>(new Map());
  loadingSubtasks  = signal<Set<string>>(new Set());
  collapsedGroups  = signal<Set<TaskStatus>>(new Set());

  groupedTasks = computed<StatusGroup[]>(() => {
    const all = this.tasks();
    return TASK_STATUS_ORDER
      .map(status => ({
        status,
        label: TASK_STATUS_LABELS[status],
        tasks: all.filter(t => t.status === status),
      }))
      .filter(g => g.tasks.length > 0);
  });

  ngOnInit(): void {
    this.loadTasks();
  }

  loadTasks(): void {
    this.loading.set(true);
    this.error.set(null);
    this.taskService.list(this.projectId(), 0, 100).subscribe({
      next:  (page) => { this.tasks.set(page.content); this.loading.set(false); },
      error: ()     => { this.error.set('Failed to load tasks.'); this.loading.set(false); },
    });
  }

  toggleGroup(status: TaskStatus): void {
    const current = this.collapsedGroups();
    const next = new Set(current);
    if (next.has(status)) {
      next.delete(status);
    } else {
      next.add(status);
    }
    this.collapsedGroups.set(next);
  }

  toggleExpand(taskId: string): void {
    const current = this.expandedTaskIds();
    const next = new Set(current);
    if (next.has(taskId)) {
      next.delete(taskId);
    } else {
      next.add(taskId);
      // Load subtasks on first expansion
      if (!this.subtaskMap().has(taskId)) {
        this.loadSubtasks(taskId);
      }
    }
    this.expandedTaskIds.set(next);
  }

  private loadSubtasks(taskId: string): void {
    const loadingSet = new Set(this.loadingSubtasks());
    loadingSet.add(taskId);
    this.loadingSubtasks.set(loadingSet);

    this.taskService.getSubtasks(taskId).subscribe({
      next: (subtasks) => {
        const newMap = new Map(this.subtaskMap());
        newMap.set(taskId, subtasks);
        this.subtaskMap.set(newMap);

        const done = new Set(this.loadingSubtasks());
        done.delete(taskId);
        this.loadingSubtasks.set(done);
      },
      error: () => {
        const newMap = new Map(this.subtaskMap());
        newMap.set(taskId, []);
        this.subtaskMap.set(newMap);

        const done = new Set(this.loadingSubtasks());
        done.delete(taskId);
        this.loadingSubtasks.set(done);
      },
    });
  }

  deleteTask(event: Event, taskId: string, title: string): void {
    event.stopPropagation();
    if (!confirm(`Delete "${title}"? This cannot be undone.`)) return;
    this.taskService.delete(taskId).subscribe({
      next:  () => this.loadTasks(),
      error: () => this.error.set('Failed to delete task.'),
    });
  }

  isOverdue(deadline: string): boolean {
    return new Date(deadline) < new Date();
  }
}
