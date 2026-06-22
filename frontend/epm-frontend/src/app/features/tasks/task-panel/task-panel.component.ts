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
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TaskService } from '../task.service';
import {
  Task,
  TaskStatus,
  TaskPriority,
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
    ReactiveFormsModule,
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
                      (click)="openTaskDrawer('TODO')">
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

              <!-- Add task button for this group -->
              <button
                class="task-group-add-btn"
                (click)="$event.stopPropagation(); openTaskDrawer(group.status)"
                [attr.aria-label]="'Add task to ' + group.label"
                type="button"
              >
                <span class="material-symbols-rounded" aria-hidden="true">add</span>
              </button>

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

                      <!-- Subtasks section — hidden when empty and no inline form open -->
                      @if (
                        loadingSubtasks().has(task.id) ||
                        (subtaskMap().get(task.id)?.length ?? 0) > 0 ||
                        activeSubtaskFormId() === task.id
                      ) {
                        <div class="task-subtasks-section">
                          <span class="task-subtasks-label">
                            <span class="material-symbols-rounded" aria-hidden="true">subdirectory_arrow_right</span>
                            Subtasks
                          </span>

                          @if (loadingSubtasks().has(task.id)) {
                            <div class="task-subtasks-loading">
                              <app-spinner size="sm" label="Loading subtasks..." />
                            </div>
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
                      }

                      <!-- Add subtask button + inline form -->
                      @if (activeSubtaskFormId() === task.id) {
                        <div class="subtask-inline-form">
                          <form [formGroup]="subtaskForm" (ngSubmit)="submitSubtaskForm(task.id)">
                            <div class="subtask-inline-row">
                              <input
                                formControlName="title"
                                placeholder="Subtask title..."
                                class="subtask-inline-input"
                              />
                              <div class="subtask-priority-group">
                                @for (p of priorities; track p.value) {
                                  <label
                                    class="subtask-priority-btn"
                                    [class.subtask-priority-btn--active]="subtaskForm.controls['priority'].value === p.value"
                                    [attr.data-priority]="p.value"
                                  >
                                    <input type="radio" formControlName="priority" [value]="p.value" class="sr-only" />
                                    <span class="material-symbols-rounded">{{ p.icon }}</span>
                                  </label>
                                }
                              </div>
                              <app-button type="submit" variant="primary" size="sm" [loading]="subtaskFormLoading()">Add</app-button>
                              <button
                                type="button"
                                class="subtask-cancel-btn"
                                (click)="closeSubtaskForm()"
                                aria-label="Cancel"
                              >
                                <span class="material-symbols-rounded">close</span>
                              </button>
                            </div>
                            @if (subtaskFormError()) {
                              <p class="subtask-form-error">{{ subtaskFormError() }}</p>
                            }
                          </form>
                        </div>
                      } @else {
                        <button
                          class="subtask-add-btn"
                          type="button"
                          (click)="$event.stopPropagation(); openSubtaskForm(task.id)"
                          aria-label="Add subtask"
                        >
                          <span class="material-symbols-rounded" aria-hidden="true">add</span>
                          Add subtask
                        </button>
                      }

                    </div>
                  </div>

                </div>

              }
            </div>

          </div>
        }

      }

    </div>

    <!-- ── Task creation drawer ──────────────────────── -->

    <!-- Backdrop -->
    <div
      class="task-drawer-backdrop"
      [class.task-drawer-backdrop--open]="drawerOpen()"
      (click)="closeTaskDrawer()"
    ></div>

    <!-- Drawer panel -->
    <aside class="task-drawer" [class.task-drawer--open]="drawerOpen()" aria-label="New task">
      <div class="task-drawer-header">
        <h2 class="task-drawer-title">New task</h2>
        <span class="task-drawer-status-chip">{{ TASK_STATUS_LABELS[drawerTaskStatus()] }}</span>
        <button class="task-drawer-close" (click)="closeTaskDrawer()" aria-label="Close drawer">
          <span class="material-symbols-rounded">close</span>
        </button>
      </div>
      <form [formGroup]="drawerForm" (ngSubmit)="submitTaskDrawer()" class="task-drawer-form">

        <!-- Title -->
        <div class="td-field">
          <label class="td-label" for="drawer-title">
            Title <span class="td-required">*</span>
          </label>
          <input
            id="drawer-title"
            type="text"
            formControlName="title"
            placeholder="What needs to be done?"
            class="td-input"
          />
          @if (drawerForm.controls['title'].hasError('required') && drawerForm.controls['title'].touched) {
            <span class="td-error">Title is required.</span>
          }
        </div>

        <!-- Description -->
        <div class="td-field">
          <label class="td-label" for="drawer-description">
            Description <span class="td-optional">optional</span>
          </label>
          <textarea
            id="drawer-description"
            formControlName="description"
            rows="3"
            placeholder="Add more context, notes..."
            class="td-input td-textarea"
          ></textarea>
        </div>

        <!-- Priority -->
        <div class="td-field">
          <label class="td-label">Priority</label>
          <div class="td-priority-group">
            @for (p of priorities; track p.value) {
              <label
                class="td-priority-btn"
                [class.td-priority-btn--active]="drawerForm.controls['priority'].value === p.value"
                [class.td-priority-btn--high]="p.value === 'HIGH' && drawerForm.controls['priority'].value === 'HIGH'"
                [class.td-priority-btn--medium]="p.value === 'MEDIUM' && drawerForm.controls['priority'].value === 'MEDIUM'"
                [class.td-priority-btn--low]="p.value === 'LOW' && drawerForm.controls['priority'].value === 'LOW'"
              >
                <input type="radio" formControlName="priority" [value]="p.value" class="sr-only" />
                <span class="material-symbols-rounded td-priority-icon">{{ p.icon }}</span>
                <span>{{ p.label }}</span>
              </label>
            }
          </div>
        </div>

        <!-- Deadline -->
        <div class="td-field">
          <label class="td-label" for="drawer-deadline">
            Deadline <span class="td-optional">optional</span>
          </label>
          <input
            id="drawer-deadline"
            type="date"
            formControlName="deadline"
            class="td-input"
          />
        </div>

        @if (drawerError()) {
          <app-error-banner [message]="drawerError()!" />
        }

        <div class="task-drawer-actions">
          <app-button type="button" variant="secondary" size="sm" (click)="closeTaskDrawer()">Cancel</app-button>
          <app-button type="submit" variant="primary" size="sm" [loading]="drawerLoading()">Create task</app-button>
        </div>

      </form>
    </aside>

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

      /* ── Group add button ──────────────────────── */
      .task-group-add-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 0.375rem;
        border: 1px solid transparent;
        background: transparent;
        cursor: pointer;
        color: var(--color-text-disabled);
        opacity: 0;
        transition: opacity 0.15s, background 0.15s, border-color 0.15s, color 0.15s;
        flex-shrink: 0;
        padding: 0;
      }
      .task-group-header:hover .task-group-add-btn {
        opacity: 1;
      }
      .task-group-add-btn:hover {
        background: color-mix(in oklch, var(--color-accent) 12%, var(--color-bg-elevated));
        border-color: color-mix(in oklch, var(--color-accent) 25%, transparent);
        color: var(--color-accent);
      }
      .task-group-add-btn .material-symbols-rounded { font-size: 0.9375rem; }

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

      /* ── Add subtask button ────────────────────── */
      .subtask-add-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem 0.625rem 0.25rem 0.375rem;
        border-radius: 0.375rem;
        border: 1px dashed color-mix(in oklch, var(--color-border) 70%, transparent);
        background: transparent;
        cursor: pointer;
        color: var(--color-text-muted);
        font-size: 0.75rem;
        font-family: 'Outfit', sans-serif;
        font-weight: 500;
        transition: border-color 0.15s, color 0.15s, background 0.15s;
        align-self: flex-start;
      }
      .subtask-add-btn:hover {
        border-color: var(--color-accent);
        color: var(--color-accent);
        background: color-mix(in oklch, var(--color-accent) 5%, transparent);
      }
      .subtask-add-btn .material-symbols-rounded { font-size: 0.875rem; }

      /* ── Inline subtask form ───────────────────── */
      .subtask-inline-form {
        padding: 0.5rem 0 0;
      }
      .subtask-inline-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .subtask-inline-input {
        flex: 1;
        padding: 0.375rem 0.625rem;
        border-radius: 0.5rem;
        border: 1px solid var(--color-border);
        background: var(--color-bg-base);
        color: var(--color-text-primary);
        font-size: 0.8125rem;
        font-family: 'Outfit', sans-serif;
        outline: none;
        transition: border-color 0.15s;
      }
      .subtask-inline-input:focus { border-color: var(--color-accent); }
      .subtask-inline-input::placeholder { color: var(--color-text-muted); }

      .subtask-priority-group {
        display: flex;
        gap: 0.25rem;
      }
      .subtask-priority-btn {
        width: 1.625rem;
        height: 1.625rem;
        display: flex; align-items: center; justify-content: center;
        border-radius: 0.375rem;
        border: 1px solid var(--color-border);
        background: var(--color-bg-elevated);
        cursor: pointer;
        color: var(--color-text-muted);
        transition: border-color 0.15s, background 0.15s;
      }
      .subtask-priority-btn--active[data-priority="HIGH"]   { border-color: var(--color-danger);  background: color-mix(in oklch, var(--color-danger)  12%, var(--color-bg-elevated)); color: var(--color-danger); }
      .subtask-priority-btn--active[data-priority="MEDIUM"] { border-color: var(--color-warning); background: color-mix(in oklch, var(--color-warning) 12%, var(--color-bg-elevated)); color: var(--color-warning); }
      .subtask-priority-btn--active[data-priority="LOW"]    { border-color: var(--color-success); background: color-mix(in oklch, var(--color-success) 12%, var(--color-bg-elevated)); color: var(--color-success); }
      .subtask-priority-btn .material-symbols-rounded { font-size: 0.875rem; }

      .subtask-cancel-btn {
        width: 1.625rem; height: 1.625rem;
        border-radius: 0.375rem; border: none; background: transparent;
        cursor: pointer; color: var(--color-text-muted);
        display: flex; align-items: center; justify-content: center;
      }
      .subtask-cancel-btn:hover { color: var(--color-text-primary); }
      .subtask-cancel-btn .material-symbols-rounded { font-size: 1rem; }

      .subtask-form-error {
        font-size: 0.75rem;
        color: var(--color-danger);
        margin: 0.25rem 0 0;
      }

      /* ── Task drawer backdrop ──────────────────── */
      .task-drawer-backdrop {
        position: fixed;
        inset: 0;
        background: oklch(0 0 0 / 0.35);
        z-index: 50;
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.25s ease;
      }
      .task-drawer-backdrop--open {
        opacity: 1;
        pointer-events: all;
      }

      /* ── Task drawer panel ─────────────────────── */
      .task-drawer {
        position: fixed;
        top: 0;
        right: 0;
        bottom: 0;
        width: 380px;
        max-width: 90vw;
        background: var(--color-bg-surface);
        border-left: 1px solid var(--color-border);
        box-shadow: var(--shadow-lg);
        z-index: 51;
        transform: translateX(100%);
        transition: transform 0.28s cubic-bezier(0.2, 0, 0, 1);
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
      .task-drawer--open {
        transform: translateX(0);
      }

      .task-drawer-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1.25rem 1.5rem;
        border-bottom: 1px solid var(--color-border);
        flex-shrink: 0;
      }

      .task-drawer-title {
        font-family: 'Outfit', sans-serif;
        font-size: 1rem;
        font-weight: 650;
        color: var(--color-text-primary);
        margin: 0;
        flex: 1;
      }

      .task-drawer-status-chip {
        font-size: 0.6875rem;
        font-weight: 600;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        padding: 0.125rem 0.5rem;
        border-radius: 9999px;
        background: color-mix(in oklch, var(--color-accent) 12%, var(--color-bg-elevated));
        color: var(--color-accent);
        border: 1px solid color-mix(in oklch, var(--color-accent) 25%, transparent);
        font-family: 'JetBrains Mono', monospace;
      }

      .task-drawer-close {
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.375rem;
        border: none;
        background: transparent;
        cursor: pointer;
        color: var(--color-text-muted);
        display: flex; align-items: center; justify-content: center;
        transition: background 0.15s, color 0.15s;
      }
      .task-drawer-close:hover {
        background: color-mix(in oklch, var(--color-bg-overlay) 60%, transparent);
        color: var(--color-text-primary);
      }
      .task-drawer-close .material-symbols-rounded { font-size: 1.125rem; }

      .task-drawer-form {
        flex: 1;
        overflow-y: auto;
        padding: 1.25rem 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }

      .task-drawer-actions {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        padding-top: 0.5rem;
      }

      /* ── Drawer field styles (td- prefix) ──────── */
      .td-field {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }

      .td-label {
        font-size: 0.8125rem;
        font-weight: 600;
        letter-spacing: 0.01em;
        color: var(--color-text-secondary);
      }

      .td-required { color: var(--color-danger); margin-left: 0.125rem; }

      .td-optional {
        font-size: 0.75rem;
        font-weight: 400;
        color: var(--color-text-muted);
        margin-left: 0.25rem;
      }

      .td-input {
        width: 100%;
        padding: 0.625rem 0.875rem;
        border-radius: 0.625rem;
        font-size: 0.875rem;
        font-family: 'Outfit', sans-serif;
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border);
        color: var(--color-text-primary);
        transition: border-color 0.18s, box-shadow 0.18s;
        outline: none;
        box-sizing: border-box;
      }
      .td-input::placeholder { color: var(--color-text-muted); }
      .td-input:focus {
        border-color: var(--color-accent);
        box-shadow: 0 0 0 3px var(--color-accent-subtle);
      }

      .td-textarea { resize: vertical; min-height: 5rem; }

      .td-error { font-size: 0.75rem; color: var(--color-danger); }

      .td-priority-group {
        display: flex;
        gap: 0.375rem;
      }

      .td-priority-btn {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.3rem;
        padding: 0.4375rem 0.375rem;
        border-radius: 0.5rem;
        border: 1px solid var(--color-border);
        background: var(--color-bg-surface);
        font-size: 0.8125rem;
        font-family: 'Outfit', sans-serif;
        color: var(--color-text-muted);
        cursor: pointer;
        transition: border-color 0.15s, background 0.15s, color 0.15s;
      }
      .td-priority-btn:hover {
        border-color: var(--color-border-strong);
        color: var(--color-text-primary);
      }

      .td-priority-icon { font-size: 0.9375rem; }

      .td-priority-btn--active.td-priority-btn--high {
        border-color: var(--color-danger);
        background: color-mix(in oklch, var(--color-danger) 10%, var(--color-bg-surface));
        color: var(--color-danger);
      }
      .td-priority-btn--active.td-priority-btn--medium {
        border-color: var(--color-warning);
        background: color-mix(in oklch, var(--color-warning) 10%, var(--color-bg-surface));
        color: var(--color-warning);
      }
      .td-priority-btn--active.td-priority-btn--low {
        border-color: var(--color-success);
        background: color-mix(in oklch, var(--color-success) 10%, var(--color-bg-surface));
        color: var(--color-success);
      }

      /* ── Accessibility ─────────────────────────── */
      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border-width: 0;
      }
    </style>
  `,
})
export class TaskPanelComponent implements OnInit {
  private readonly taskService = inject(TaskService);
  private readonly fb          = inject(FormBuilder);

  projectId = input.required<string>();

  // ── Task list state ──────────────────────────────────────
  tasks   = signal<Task[]>([]);
  loading = signal(false);
  error   = signal<string | null>(null);

  expandedTaskIds  = signal<Set<string>>(new Set());
  subtaskMap       = signal<Map<string, Task[]>>(new Map());
  loadingSubtasks  = signal<Set<string>>(new Set());
  collapsedGroups  = signal<Set<TaskStatus>>(new Set());

  // ── Drawer state ─────────────────────────────────────────
  drawerOpen       = signal(false);
  drawerTaskStatus = signal<TaskStatus>('TODO');
  drawerLoading    = signal(false);
  drawerError      = signal<string | null>(null);
  drawerForm: FormGroup;

  // ── Subtask inline form state ────────────────────────────
  activeSubtaskFormId = signal<string | null>(null);
  subtaskFormLoading  = signal(false);
  subtaskFormError    = signal<string | null>(null);
  subtaskForm: FormGroup;

  // ── Priority options (shared by drawer and subtask form) ─
  readonly priorities: { value: TaskPriority; label: string; icon: string }[] = [
    { value: 'HIGH',   label: 'High',   icon: 'keyboard_double_arrow_up' },
    { value: 'MEDIUM', label: 'Medium', icon: 'drag_handle' },
    { value: 'LOW',    label: 'Low',    icon: 'keyboard_double_arrow_down' },
  ];

  // Expose for template
  readonly TASK_STATUS_LABELS = TASK_STATUS_LABELS;

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

  constructor() {
    this.drawerForm = this.fb.nonNullable.group({
      title:       ['', [Validators.required, Validators.minLength(1)]],
      description: [''],
      priority:    ['MEDIUM' as TaskPriority, Validators.required],
      deadline:    [''],
    });

    this.subtaskForm = this.fb.nonNullable.group({
      title:    ['', [Validators.required, Validators.minLength(1)]],
      priority: ['MEDIUM' as TaskPriority, Validators.required],
    });
  }

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

  // ── Drawer methods ───────────────────────────────────────

  openTaskDrawer(status: TaskStatus): void {
    this.drawerTaskStatus.set(status);
    this.drawerForm.reset({
      title:       '',
      description: '',
      priority:    'MEDIUM' as TaskPriority,
      deadline:    '',
    });
    this.drawerError.set(null);
    this.drawerOpen.set(true);
  }

  closeTaskDrawer(): void {
    this.drawerOpen.set(false);
    this.drawerForm.reset();
    this.drawerError.set(null);
  }

  submitTaskDrawer(): void {
    if (this.drawerForm.invalid) {
      this.drawerForm.markAllAsTouched();
      return;
    }

    this.drawerLoading.set(true);
    this.drawerError.set(null);

    const { title, description, priority, deadline } = this.drawerForm.getRawValue();

    this.taskService.create({
      projectId:   this.projectId(),
      title,
      description: description || undefined,
      priority,
      deadline:    deadline || undefined,
    }).subscribe({
      next: () => {
        this.drawerLoading.set(false);
        this.closeTaskDrawer();
        this.loadTasks();
      },
      error: () => {
        this.drawerError.set('Failed to create task. Please try again.');
        this.drawerLoading.set(false);
      },
    });
  }

  // ── Subtask form methods ─────────────────────────────────

  openSubtaskForm(taskId: string): void {
    this.activeSubtaskFormId.set(taskId);
    this.subtaskForm.reset({
      title:    '',
      priority: 'MEDIUM' as TaskPriority,
    });
    this.subtaskFormError.set(null);
  }

  closeSubtaskForm(): void {
    this.activeSubtaskFormId.set(null);
    this.subtaskForm.reset();
  }

  submitSubtaskForm(parentTaskId: string): void {
    if (this.subtaskForm.invalid) {
      this.subtaskForm.markAllAsTouched();
      return;
    }

    this.subtaskFormLoading.set(true);
    this.subtaskFormError.set(null);

    const { title, priority } = this.subtaskForm.getRawValue();

    this.taskService.createSubtask({
      parentTaskId,
      title,
      priority,
    }).subscribe({
      next: () => {
        this.subtaskFormLoading.set(false);
        this.closeSubtaskForm();
        // Reload subtasks for this task
        this.taskService.getSubtasks(parentTaskId).subscribe({
          next: (subtasks) => {
            const newMap = new Map(this.subtaskMap());
            newMap.set(parentTaskId, subtasks);
            this.subtaskMap.set(newMap);
          },
        });
      },
      error: () => {
        this.subtaskFormError.set('Failed to create subtask. Please try again.');
        this.subtaskFormLoading.set(false);
      },
    });
  }
}
