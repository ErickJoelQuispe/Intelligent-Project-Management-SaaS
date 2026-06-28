import { Component, ChangeDetectionStrategy, input, computed, inject } from '@angular/core';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { BadgeComponent, BadgeVariant } from '../badge/badge.component';
import { TaskStatus, TASK_STATUS_KEYS } from '../../../core/models/task.models';

const VARIANT_MAP: Record<TaskStatus, BadgeVariant> = {
  TODO:        'neutral',
  IN_PROGRESS: 'info',
  IN_REVIEW:   'warning',
  DONE:        'success',
  CANCELLED:   'danger',
};

@Component({
  selector: 'app-task-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BadgeComponent, TranslocoPipe],
  template: `
    <app-badge [variant]="variant()">
      {{ TASK_STATUS_KEYS[status()] | transloco }}
    </app-badge>
  `,
})
export class TaskStatusBadgeComponent {
  private readonly translocoService = inject(TranslocoService);

  status = input.required<TaskStatus>();

  variant = computed(() => VARIANT_MAP[this.status()]);

  readonly TASK_STATUS_KEYS = TASK_STATUS_KEYS;

  label = computed(() => this.translocoService.translate(TASK_STATUS_KEYS[this.status()]));
}
