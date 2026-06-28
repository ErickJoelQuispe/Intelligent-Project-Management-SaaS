import {
  Component,
  ChangeDetectionStrategy,
  ViewEncapsulation,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { TaskService } from '../task.service';
import {
  TaskStatus,
  TaskPriority,
  TASK_STATUS_KEYS,
} from '../../../core/models/task.models';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';

@Component({
  selector: 'app-task-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    ErrorBannerComponent,
    TranslocoPipe,
  ],
  template: `
    <!-- Backdrop -->
    <div class="task-drawer-backdrop"
         [class.task-drawer-backdrop--open]="open()"
         (click)="close()"
         aria-hidden="true">
    </div>

    <!-- Drawer -->
    <aside class="task-drawer"
           [class.task-drawer--open]="open()"
           role="dialog"
           aria-modal="true"
           [attr.aria-label]="'tasks.drawer.newTask' | transloco">

      <div class="task-drawer-header">
        <h2 class="task-drawer-title">{{ 'tasks.drawer.newTask' | transloco }}</h2>
        <span class="task-drawer-status-chip">{{ TASK_STATUS_KEYS[taskStatus()] | transloco }}</span>
        <button class="task-drawer-close" (click)="close()" [attr.aria-label]="'tasks.drawer.close' | transloco">
          <span class="material-symbols-rounded">close</span>
        </button>
      </div>

      <form [formGroup]="drawerForm" (ngSubmit)="submit()" class="task-drawer-form" novalidate>

        <div class="td-field">
          <label class="td-label" for="td-title">{{ 'tasks.form.titleLabel' | transloco }} <span class="td-required">*</span></label>
          <input id="td-title" type="text" formControlName="title"
                 [placeholder]="'tasks.form.titlePlaceholder' | transloco" class="td-input" />
          @if (drawerForm.controls['title'].hasError('required') && drawerForm.controls['title'].touched) {
            <span class="td-error">{{ 'tasks.form.titleRequired' | transloco }}</span>
          }
        </div>

        <div class="td-field">
          <label class="td-label" for="td-description">{{ 'tasks.form.descriptionLabel' | transloco }} <span class="td-optional">{{ 'common.optional' | transloco }}</span></label>
          <textarea id="td-description" formControlName="description" rows="3"
                    [placeholder]="'tasks.form.descriptionPlaceholder' | transloco" class="td-input td-textarea"></textarea>
        </div>

        <div class="td-field">
          <label class="td-label">{{ 'tasks.form.priorityLabel' | transloco }}</label>
          <div class="td-priority-group">
            @for (p of priorities; track p.value) {
              <label class="td-priority-btn"
                     [class.td-priority-btn--active]="drawerForm.controls['priority'].value === p.value"
                     [class.td-priority-btn--high]="p.value === 'HIGH' && drawerForm.controls['priority'].value === 'HIGH'"
                     [class.td-priority-btn--medium]="p.value === 'MEDIUM' && drawerForm.controls['priority'].value === 'MEDIUM'"
                     [class.td-priority-btn--low]="p.value === 'LOW' && drawerForm.controls['priority'].value === 'LOW'">
                <input type="radio" formControlName="priority" [value]="p.value" class="sr-only" />
                <span class="material-symbols-rounded td-priority-icon">{{ p.icon }}</span>
                <span>{{ p.label | transloco }}</span>
              </label>
            }
          </div>
        </div>

        <div class="td-field">
          <label class="td-label" for="td-deadline">{{ 'tasks.form.deadlineLabel' | transloco }} <span class="td-optional">{{ 'common.optional' | transloco }}</span></label>
          <input id="td-deadline" type="date" formControlName="deadline" class="td-input" />
        </div>

        @if (error()) {
          <app-error-banner [message]="error()!" />
        }

        <div class="task-drawer-actions">
          <app-button type="button" variant="secondary" size="sm" (click)="close()">{{ 'tasks.form.cancelBtn' | transloco }}</app-button>
          <app-button type="submit" variant="primary" size="sm" [loading]="loading()">{{ 'tasks.form.submitBtn' | transloco }}</app-button>
        </div>

      </form>
    </aside>
  `,
  styles: [`
    .task-drawer-backdrop {
      position: fixed;
      inset: 0;
      background: oklch(0 0 0 / 0.4);
      z-index: 100;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.25s ease;
    }
    .task-drawer-backdrop--open {
      opacity: 1;
      pointer-events: all;
    }
    .task-drawer {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      width: 400px;
      max-width: 92vw;
      background: var(--bg-elevated);
      border-left: 1px solid var(--color-border);
      box-shadow: -4px 0 40px oklch(0 0 0 / 0.3);
      z-index: 101;
      transform: translateX(100%);
      transition: transform 0.28s cubic-bezier(0.2, 0, 0, 1);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .task-drawer--open { transform: translateX(0); }

    .task-drawer-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid var(--color-border);
      flex-shrink: 0;
      background: var(--bg-elevated);
    }
    .task-drawer-title {
      font-family: 'Outfit', sans-serif;
      font-size: 1rem;
      font-weight: 650;
      color: var(--color-text-primary);
      margin: 0;
      flex: 1;
      letter-spacing: -0.01em;
    }
    .task-drawer-status-chip {
      font-size: 0.6875rem;
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      padding: 0.125rem 0.5rem;
      border-radius: 9999px;
      background: color-mix(in oklch, var(--color-accent) 12%, var(--bg-elevated));
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
      display: flex;
      align-items: center;
      justify-content: center;
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
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.125rem;
      background: var(--bg-elevated);
    }
    .task-drawer-actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
      padding-top: 0.25rem;
    }

    /* Field styles — td- prefix */
    .td-field { display: flex; flex-direction: column; gap: 0.4rem; }
    .td-label {
      font-size: 0.8125rem;
      font-weight: 600;
      letter-spacing: 0.01em;
      color: var(--color-text-secondary);
    }
    .td-required { color: var(--color-danger); margin-left: 0.125rem; }
    .td-optional { font-size: 0.75rem; font-weight: 400; color: var(--color-text-muted); margin-left: 0.25rem; }
    .td-input {
      width: 100%;
      padding: 0.625rem 0.875rem;
      border-radius: 0.625rem;
      font-size: 0.875rem;
      font-family: 'Outfit', sans-serif;
      background: var(--bg-surface);
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
    .td-error { font-size: 0.75rem; color: var(--color-danger); margin-top: 0.125rem; }

    .td-priority-group { display: flex; gap: 0.375rem; }
    .td-priority-btn {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.3rem;
      padding: 0.5rem 0.375rem;
      border-radius: 0.5rem;
      border: 1px solid var(--color-border);
      background: var(--bg-surface);
      font-size: 0.8125rem;
      font-family: 'Outfit', sans-serif;
      color: var(--color-text-muted);
      cursor: pointer;
      transition: border-color 0.15s, background 0.15s, color 0.15s;
      user-select: none;
    }
    .td-priority-btn:hover { border-color: var(--color-border-strong); color: var(--color-text-primary); }
    .td-priority-icon { font-size: 0.9375rem; }
    .td-priority-btn--active.td-priority-btn--high {
      border-color: var(--color-danger);
      background: color-mix(in oklch, var(--color-danger) 10%, var(--bg-surface));
      color: var(--color-danger);
    }
    .td-priority-btn--active.td-priority-btn--medium {
      border-color: var(--color-warning);
      background: color-mix(in oklch, var(--color-warning) 10%, var(--bg-surface));
      color: var(--color-warning);
    }
    .td-priority-btn--active.td-priority-btn--low {
      border-color: var(--color-success);
      background: color-mix(in oklch, var(--color-success) 10%, var(--bg-surface));
      color: var(--color-success);
    }
    .sr-only {
      position: absolute; width: 1px; height: 1px;
      padding: 0; margin: -1px; overflow: hidden;
      clip: rect(0,0,0,0); white-space: nowrap; border-width: 0;
    }
  `],
})
export class TaskDrawerComponent {
  private readonly taskService      = inject(TaskService);
  private readonly fb               = inject(FormBuilder);
  private readonly translocoService = inject(TranslocoService);

  // Inputs
  open      = input<boolean>(false);
  taskStatus = input<TaskStatus>('TODO');
  projectId  = input.required<string>();

  // Outputs
  closed      = output<void>();
  taskCreated = output<void>();

  // Internal state
  loading = signal(false);
  error   = signal<string | null>(null);

  // Form
  drawerForm: FormGroup;

  // Priority options — labels are i18n keys resolved via the transloco pipe
  readonly priorities: { value: TaskPriority; label: string; icon: string }[] = [
    { value: 'HIGH',   label: 'tasks.priority.high',   icon: 'keyboard_double_arrow_up' },
    { value: 'MEDIUM', label: 'tasks.priority.medium', icon: 'drag_handle' },
    { value: 'LOW',    label: 'tasks.priority.low',    icon: 'keyboard_double_arrow_down' },
  ];

  // Expose status keys for template
  readonly TASK_STATUS_KEYS = TASK_STATUS_KEYS;

  constructor() {
    this.drawerForm = this.fb.nonNullable.group({
      title:       ['', [Validators.required, Validators.minLength(1)]],
      description: [''],
      priority:    ['MEDIUM' as TaskPriority, Validators.required],
      deadline:    [''],
    });
  }

  close(): void {
    this.closed.emit();
    this.drawerForm.reset({
      title:       '',
      description: '',
      priority:    'MEDIUM' as TaskPriority,
      deadline:    '',
    });
    this.error.set(null);
  }

  submit(): void {
    if (this.drawerForm.invalid) {
      this.drawerForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    const { title, description, priority, deadline } = this.drawerForm.getRawValue();

    this.taskService.create({
      projectId:   this.projectId(),
      title,
      description: description || undefined,
      priority,
      deadline:    deadline || undefined,
    }).subscribe({
      next: () => {
        this.loading.set(false);
        this.taskCreated.emit();
        this.close();
      },
      error: () => {
        this.error.set(this.translocoService.translate('tasks.form.createError'));
        this.loading.set(false);
      },
    });
  }
}
