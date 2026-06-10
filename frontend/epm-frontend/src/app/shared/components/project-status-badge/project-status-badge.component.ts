import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../badge/badge.component';
import { ProjectStatus } from '../../../core/models/project.model';

const VARIANT_MAP: Record<ProjectStatus, BadgeVariant> = {
  [ProjectStatus.ACTIVE]:    'success',
  [ProjectStatus.COMPLETED]: 'info',
  [ProjectStatus.ARCHIVED]:  'neutral',
};

const LABEL_MAP: Record<ProjectStatus, string> = {
  [ProjectStatus.ACTIVE]:    'Active',
  [ProjectStatus.COMPLETED]: 'Completed',
  [ProjectStatus.ARCHIVED]:  'Archived',
};

@Component({
  selector: 'app-project-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BadgeComponent],
  template: `
    <app-badge [variant]="variant()" [dot]="true">
      {{ label() }}
    </app-badge>
  `,
})
export class ProjectStatusBadgeComponent {
  status = input.required<ProjectStatus>();

  variant = computed(() => VARIANT_MAP[this.status()]);
  label   = computed(() => LABEL_MAP[this.status()]);
}
