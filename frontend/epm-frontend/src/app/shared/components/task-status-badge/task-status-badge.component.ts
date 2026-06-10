import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../badge/badge.component';
import { TaskStatus, TASK_STATUS_LABELS } from '../../../core/models/task.models';

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
  imports: [BadgeComponent],
  template: `
    <app-badge [variant]="variant()">
      {{ label() }}
    </app-badge>
  `,
})
export class TaskStatusBadgeComponent {
  status = input.required<TaskStatus>();

  variant = computed(() => VARIANT_MAP[this.status()]);

  // Reutilizamos el mapa que YA existe en el modelo — no duplicamos
  label = computed(() => TASK_STATUS_LABELS[this.status()]);
}
