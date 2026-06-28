import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize    = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      [attr.data-variant]="variant()"
      [attr.data-size]="size()"
      [class]="classes()"
    >
      @if (loading()) {
        <span class="epm-btn-spinner" aria-hidden="true"></span>
      }
      <ng-content />
    </button>
  `,
  styles: [`
    /* Base */
    button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.375rem;
      font-family: 'Outfit', sans-serif;
      font-weight: 600;
      letter-spacing: 0.01em;
      border-radius: 0.625rem;
      border: none;          /* sin border por defecto — cada variante define el suyo */
      cursor: pointer;
      transition: all 0.18s cubic-bezier(0.2, 0, 0, 1);
      outline: none;
      position: relative;
      overflow: hidden;
      white-space: nowrap;
      -webkit-font-smoothing: antialiased;
    }

    button:disabled {
      opacity: 0.45;
      cursor: not-allowed;
      pointer-events: none;
    }

    button:focus-visible {
      outline: 2px solid var(--color-accent);
      outline-offset: 2px;
    }

    /* ── Sizes ── */
    button[data-size="sm"] { padding: 0.375rem 0.875rem; font-size: 0.75rem; }
    button[data-size="md"] { padding: 0.5rem  1.125rem;  font-size: 0.875rem; }
    button[data-size="lg"] { padding: 0.75rem 1.5rem;    font-size: 1rem; }

    /* ── PRIMARY — outlined with soft accent fill, theme-aware ── */
    button[data-variant="primary"] {
      background: color-mix(in oklch, var(--color-accent) 10%, transparent);
      color: var(--color-accent-hover);
      border: 1.5px solid var(--color-accent);
      box-shadow: none;
      font-weight: 650;
    }

    /* No shimmer needed for outlined style */
    button[data-variant="primary"]::before {
      content: none;
    }

    button[data-variant="primary"]:hover:not(:disabled) {
      background: color-mix(in oklch, var(--color-accent) 16%, transparent);
      border-color: var(--color-accent-hover);
      color: var(--color-accent-hover);
      box-shadow: 0 0 16px color-mix(in oklch, var(--color-accent) 25%, transparent);
      transform: translateY(-1px);
    }

    button[data-variant="primary"]:active:not(:disabled) {
      transform: translateY(0px);
      background: color-mix(in oklch, var(--color-accent) 20%, transparent);
      box-shadow: none;
    }

    /* ── SECONDARY — glass, borde sutil con tinte violeta ── */
    button[data-variant="secondary"] {
      background: color-mix(in oklch, var(--color-bg-surface) 80%, transparent);
      color: var(--color-text-primary);
      border: 1px solid var(--color-border-strong);
      backdrop-filter: blur(8px);
      box-shadow: 0 1px 0 oklch(1 0 0 / 0.05) inset;
    }

    button[data-variant="secondary"]:hover:not(:disabled) {
      background: var(--color-bg-elevated);
      color: var(--color-text-primary);
      border-color: color-mix(in oklch, var(--color-accent) 45%, transparent);
      box-shadow:
        0 1px 0 oklch(1 0 0 / 0.07) inset,
        0 0 12px color-mix(in oklch, var(--color-accent) 15%, transparent);
      transform: translateY(-1px);
    }

    button[data-variant="secondary"]:active:not(:disabled) { transform: translateY(0); }

    /* ── GHOST — sin borde por defecto, aparece en hover ── */
    button[data-variant="ghost"] {
      background: transparent;
      color: var(--color-text-secondary);
      border: 1px solid transparent;
    }

    button[data-variant="ghost"]:hover:not(:disabled) {
      background: color-mix(in oklch, var(--color-accent) 8%, transparent);
      color: var(--color-text-primary);
      border-color: color-mix(in oklch, var(--color-accent) 18%, transparent);
    }

    button[data-variant="ghost"]:active:not(:disabled) {
      background: color-mix(in oklch, var(--color-accent) 13%, transparent);
    }

    /* ── DANGER ── */
    button[data-variant="danger"] {
      background: var(--color-danger-subtle);
      color: var(--color-danger);
      border: 1px solid color-mix(in oklch, var(--color-danger) 28%, transparent);
    }

    button[data-variant="danger"]:hover:not(:disabled) {
      background: var(--color-danger);
      color: white;
      border-color: transparent;
      box-shadow: 0 0 18px color-mix(in oklch, var(--color-danger) 45%, transparent);
      transform: translateY(-1px);
    }

    button[data-variant="danger"]:active:not(:disabled) { transform: translateY(0); }

    /* ── Spinner inline ── */
    .epm-btn-spinner {
      display: block;
      width: 0.875rem;
      height: 0.875rem;
      border-radius: 9999px;
      flex-shrink: 0;
      animation: btn-spin 0.65s linear infinite;
      background: conic-gradient(
        from 0deg,
        currentColor 0%,
        transparent 70%
      );
      mask: radial-gradient(farthest-side, transparent 55%, black 56%);
      -webkit-mask: radial-gradient(farthest-side, transparent 55%, black 56%);
    }

    @keyframes btn-spin {
      to { transform: rotate(360deg); }
    }
  `],
})
export class ButtonComponent {
  variant  = input<ButtonVariant>('primary');
  size     = input<ButtonSize>('md');
  type     = input<'button' | 'submit' | 'reset'>('button');
  disabled = input<boolean>(false);
  loading  = input<boolean>(false);

  // Solo mantenemos clases para lo que NO podemos hacer con data-attrs en CSS
  private readonly base =
    'focus-visible:outline-2 focus-visible:outline-offset-2';

  classes = computed(() => this.base);
}
