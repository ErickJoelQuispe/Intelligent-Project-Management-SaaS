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
    style:   'background: var(--color-accent-subtle); color: var(--color-accent); border-color: color-mix(in oklch, var(--color-accent) 30%, transparent); box-shadow: 0 0 8px color-mix(in oklch, var(--color-accent) 25%, transparent);',
  },
  MEMBER: {
    label:   'Member',
    classes: 'inline-flex items-center px-2 py-0.5 rounded-full text-[0.65rem] font-semibold border whitespace-nowrap tracking-wide bg-info-subtle text-info border-info/20',
    style:   'box-shadow: 0 0 8px color-mix(in oklch, var(--color-info) 25%, transparent);',
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
