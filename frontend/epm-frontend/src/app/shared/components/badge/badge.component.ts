import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

export type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';
export type BadgeSize    = 'sm' | 'md';

@Component({
  selector: 'app-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="classes()" [style]="styles()">
      @if (dot()) {
        <span class="size-1.5 rounded-full bg-current shrink-0 animate-glow-pulse"></span>
      }
      <ng-content />
    </span>
  `,
})
export class BadgeComponent {
  variant = input<BadgeVariant>('neutral');
  size    = input<BadgeSize>('md');
  dot     = input<boolean>(false);

  private readonly base =
    'inline-flex items-center gap-1.5 font-semibold rounded-full ' +
    'border whitespace-nowrap tracking-wide';

  private readonly variantClasses: Record<BadgeVariant, string> = {
    success: 'bg-success-subtle  text-success  border-success/20',
    warning: 'bg-warning-subtle  text-warning  border-warning/20',
    danger:  'bg-danger-subtle   text-danger   border-danger/20',
    info:    'bg-info-subtle     text-info     border-info/20',
    neutral: 'bg-bg-elevated     text-text-secondary border-border',
  };

  // Glow sutil en los badges de estado — hace que "salten" del fondo oscuro
  private readonly variantStyles: Record<BadgeVariant, string> = {
    success: 'box-shadow: 0 0 8px oklch(0.74 0.18 152 / 0.25);',
    warning: 'box-shadow: 0 0 8px oklch(0.82 0.18 78 / 0.25);',
    danger:  'box-shadow: 0 0 8px oklch(0.65 0.24 22 / 0.25);',
    info:    'box-shadow: 0 0 8px oklch(0.72 0.16 225 / 0.25);',
    neutral: '',
  };

  private readonly sizeClasses: Record<BadgeSize, string> = {
    sm: 'px-2   py-0.5 text-[0.65rem]',
    md: 'px-2.5 py-0.5 text-xs',
  };

  classes = computed(() =>
    [this.base, this.variantClasses[this.variant()], this.sizeClasses[this.size()]].join(' ')
  );

  styles = computed(() => this.variantStyles[this.variant()]);
}
