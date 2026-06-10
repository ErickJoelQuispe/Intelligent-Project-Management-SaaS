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
      outline: 2px solid oklch(0.65 0.26 285);
      outline-offset: 2px;
    }

    /* ── Sizes ── */
    button[data-size="sm"] { padding: 0.375rem 0.875rem; font-size: 0.75rem; }
    button[data-size="md"] { padding: 0.5rem  1.125rem;  font-size: 0.875rem; }
    button[data-size="lg"] { padding: 0.75rem 1.5rem;    font-size: 1rem; }

    /* ── PRIMARY — gradiente violet→indigo, sin border visible ── */
    button[data-variant="primary"] {
      background: linear-gradient(135deg,
        oklch(0.62 0.28 290) 0%,
        oklch(0.56 0.26 270) 55%,
        oklch(0.50 0.24 255) 100%
      );
      color: white;
      border-color: transparent;
      /* Brillo interno superior — da sensación de profundidad */
      box-shadow:
        0 1px 0 oklch(1 0 0 / 0.18) inset,
        0 -1px 0 oklch(0 0 0 / 0.25) inset,
        0 0 20px oklch(0.65 0.26 285 / 0.28),
        0 2px 8px oklch(0 0 0 / 0.5);
    }

    /* Shimmer en la mitad superior — solo visual, no interactivo */
    button[data-variant="primary"]::before {
      content: '';
      position: absolute;
      top: 0; left: 0; right: 0;
      height: 50%;
      background: linear-gradient(180deg, oklch(1 0 0 / 0.07) 0%, transparent 100%);
      border-radius: 0.625rem 0.625rem 0 0;
      pointer-events: none;
    }

    button[data-variant="primary"]:hover:not(:disabled) {
      background: linear-gradient(135deg,
        oklch(0.67 0.28 290) 0%,
        oklch(0.61 0.26 270) 55%,
        oklch(0.55 0.24 255) 100%
      );
      box-shadow:
        0 1px 0 oklch(1 0 0 / 0.2) inset,
        0 -1px 0 oklch(0 0 0 / 0.2) inset,
        0 0 28px oklch(0.65 0.26 285 / 0.5),
        0 4px 16px oklch(0 0 0 / 0.4);
      transform: translateY(-1px);
    }

    button[data-variant="primary"]:active:not(:disabled) {
      transform: translateY(0px);
      box-shadow:
        0 1px 0 oklch(0 0 0 / 0.2) inset,
        0 0 12px oklch(0.65 0.26 285 / 0.3);
    }

    /* ── SECONDARY — glass, borde sutil con tinte violeta ── */
    button[data-variant="secondary"] {
      background: oklch(0.14 0.025 268 / 0.8);
      color: oklch(0.82 0.012 268);
      border: 1px solid oklch(0.28 0.022 268);
      backdrop-filter: blur(8px);
      box-shadow: 0 1px 0 oklch(1 0 0 / 0.05) inset;
    }

    button[data-variant="secondary"]:hover:not(:disabled) {
      background: oklch(0.18 0.025 268);
      color: oklch(0.96 0.006 268);
      border-color: oklch(0.65 0.26 285 / 0.45);
      box-shadow:
        0 1px 0 oklch(1 0 0 / 0.07) inset,
        0 0 12px oklch(0.65 0.26 285 / 0.15);
      transform: translateY(-1px);
    }

    button[data-variant="secondary"]:active:not(:disabled) { transform: translateY(0); }

    /* ── GHOST — sin borde por defecto, aparece en hover ── */
    button[data-variant="ghost"] {
      background: transparent;
      color: oklch(0.58 0.015 268);
      border: 1px solid transparent;
    }

    button[data-variant="ghost"]:hover:not(:disabled) {
      background: oklch(0.65 0.26 285 / 0.08);
      color: oklch(0.90 0.008 268);
      border-color: oklch(0.65 0.26 285 / 0.18);
    }

    button[data-variant="ghost"]:active:not(:disabled) {
      background: oklch(0.65 0.26 285 / 0.13);
    }

    /* ── DANGER ── */
    button[data-variant="danger"] {
      background: oklch(0.65 0.24 22 / 0.1);
      color: oklch(0.72 0.22 22);
      border: 1px solid oklch(0.65 0.24 22 / 0.28);
    }

    button[data-variant="danger"]:hover:not(:disabled) {
      background: oklch(0.65 0.24 22);
      color: white;
      border-color: transparent;
      box-shadow: 0 0 18px oklch(0.65 0.24 22 / 0.45);
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
