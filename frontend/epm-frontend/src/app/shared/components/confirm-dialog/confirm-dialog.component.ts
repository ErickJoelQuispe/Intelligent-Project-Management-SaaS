import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonComponent } from '../button/button.component';

export interface ConfirmDialogConfig {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'warning';
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, TranslocoPipe],
  template: `
    <!-- Backdrop -->
    <div class="backdrop" (click)="onCancel()" aria-hidden="true"></div>

    <!-- Dialog -->
    <div
      class="dialog"
      role="alertdialog"
      aria-modal="true"
      [attr.aria-labelledby]="'cdlg-title'"
      [attr.aria-describedby]="'cdlg-msg'"
    >
      <!-- Icon -->
      <div class="dialog-icon" [class.dialog-icon--warning]="config().variant === 'warning'">
        <span class="material-symbols-rounded">
          {{ config().variant === 'warning' ? 'warning' : 'delete' }}
        </span>
      </div>

      <!-- Content -->
      <div class="dialog-body">
        <h3 id="cdlg-title" class="dialog-title">{{ config().title }}</h3>
        <p id="cdlg-msg" class="dialog-message">{{ config().message }}</p>
      </div>

      <!-- Actions -->
      <div class="dialog-actions">
        <app-button variant="secondary" size="md" (click)="onCancel()">
          {{ config().cancelLabel ?? ('confirmDialog.cancel' | transloco) }}
        </app-button>
        <app-button
          [variant]="config().variant === 'warning' ? 'primary' : 'danger'"
          size="md"
          (click)="onConfirm()"
        >
          {{ config().confirmLabel ?? ('confirmDialog.confirm' | transloco) }}
        </app-button>
      </div>
    </div>
  `,
  styles: [`
    :host {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      animation: cdlg-in 0.15s ease-out both;
    }

    @keyframes cdlg-in {
      from { opacity: 0; }
      to   { opacity: 1; }
    }

    .backdrop {
      position: absolute;
      inset: 0;
      background: oklch(0 0 0 / 0.55);
      backdrop-filter: blur(4px);
    }

    .dialog {
      position: relative;
      z-index: 1;
      width: 100%;
      max-width: 26rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      padding: 1.75rem;
      border-radius: 1.25rem;
      background: var(--color-bg-elevated);
      border: 1px solid var(--color-border-strong);
      box-shadow:
        0 0 0 1px oklch(1 0 0 / 0.04) inset,
        0 24px 64px oklch(0 0 0 / 0.55),
        0 0 80px color-mix(in oklch, var(--color-accent) 8%, transparent);
      animation: cdlg-slide 0.18s cubic-bezier(0.2, 0, 0, 1) both;
    }

    @keyframes cdlg-slide {
      from { transform: translateY(8px) scale(0.97); opacity: 0; }
      to   { transform: translateY(0)   scale(1);    opacity: 1; }
    }

    .dialog-icon {
      width: 2.75rem;
      height: 2.75rem;
      border-radius: 0.875rem;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--color-danger-subtle);
      border: 1px solid color-mix(in oklch, var(--color-danger) 25%, transparent);
      flex-shrink: 0;

      .material-symbols-rounded {
        font-size: 1.25rem;
        color: var(--color-danger);
      }

      &--warning {
        background: color-mix(in oklch, var(--color-warning, oklch(0.78 0.16 70)) 12%, transparent);
        border-color: color-mix(in oklch, var(--color-warning, oklch(0.78 0.16 70)) 25%, transparent);

        .material-symbols-rounded {
          color: var(--color-warning, oklch(0.78 0.16 70));
        }
      }
    }

    .dialog-body {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }

    .dialog-title {
      font-family: 'Outfit', sans-serif;
      font-size: 1rem;
      font-weight: 700;
      color: var(--color-text-primary);
      margin: 0;
      letter-spacing: -0.01em;
    }

    .dialog-message {
      font-size: 0.875rem;
      color: var(--color-text-secondary);
      margin: 0;
      line-height: 1.5;
    }

    .dialog-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.625rem;
    }
  `],
})
export class ConfirmDialogComponent {
  config   = input.required<ConfirmDialogConfig>();
  confirmed = output<boolean>();

  onConfirm(): void { this.confirmed.emit(true);  }
  onCancel():  void { this.confirmed.emit(false); }
}
