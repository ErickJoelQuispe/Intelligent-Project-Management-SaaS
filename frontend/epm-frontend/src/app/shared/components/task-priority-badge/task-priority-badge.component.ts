import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../badge/badge.component';
import { TaskPriority } from '../../../core/models/task.models';

const VARIANT_MAP: Record<TaskPriority, BadgeVariant> = {
  HIGH:   'danger',
  MEDIUM: 'warning',
  LOW:    'info',
};

const LABEL_MAP: Record<TaskPriority, string> = {
  HIGH:   'High',
  MEDIUM: 'Medium',
  LOW:    'Low',
};

@Component({
  selector: 'app-task-priority-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BadgeComponent],
  template: `
    <app-badge [variant]="variant()" size="sm">
      {{ label() }}
    </app-badge>
  `,
})
export class TaskPriorityBadgeComponent {
  priority = input.required<TaskPriority>();

  variant = computed(() => VARIANT_MAP[this.priority()]);
  label   = computed(() => LABEL_MAP[this.priority()]);
}
