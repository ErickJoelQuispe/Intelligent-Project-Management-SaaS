import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { TeamRole } from '../../../core/models/team.model';

interface RoleStyle {
  label: string;
  classes: string;
  style: string;
}

const ROLE_MAP: Record<TeamRole, RoleStyle> = {
  OWNER:  {
    label:   'Owner',
    classes: 'inline-flex items-center px-2 py-0.5 rounded-full text-[0.65rem] font-semibold border whitespace-nowrap tracking-wide',
    style:   'background: oklch(0.65 0.26 285 / 0.15); color: oklch(0.78 0.20 285); border-color: oklch(0.65 0.26 285 / 0.3); box-shadow: 0 0 8px oklch(0.65 0.26 285 / 0.25);',
  },
  MEMBER: {
    label:   'Member',
    classes: 'inline-flex items-center px-2 py-0.5 rounded-full text-[0.65rem] font-semibold border whitespace-nowrap tracking-wide bg-info-subtle text-info border-info/20',
    style:   'box-shadow: 0 0 8px oklch(0.72 0.16 225 / 0.25);',
  },
  VIEWER: {
    label:   'Viewer',
    classes: 'inline-flex items-center px-2 py-0.5 rounded-full text-[0.65rem] font-semibold border whitespace-nowrap tracking-wide bg-bg-elevated text-text-secondary border-border',
    style:   '',
  },
};

@Component({
  selector: 'app-team-role-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="config().classes" [style]="config().style">
      {{ config().label }}
    </span>
  `,
})
export class TeamRoleBadgeComponent {
  role = input.required<TeamRole>();

  config = computed(() => ROLE_MAP[this.role()]);
}
