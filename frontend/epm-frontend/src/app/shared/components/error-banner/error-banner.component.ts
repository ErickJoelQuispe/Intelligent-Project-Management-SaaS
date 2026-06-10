import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
} from '@angular/core';
import { ButtonComponent } from '../button/button.component';

export type ErrorBannerVariant = 'banner' | 'inline';

@Component({
  selector: 'app-error-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent],
  template: `
    <div [class]="wrapperClasses()" role="alert">

      <div class="flex items-start gap-3">

        <!-- Ícono — siempre presente -->
        <span class="material-symbols-rounded text-danger shrink-0 mt-0.5"
              [class]="variant() === 'banner' ? 'text-2xl' : 'text-lg'">
          error
        </span>

        <!-- Texto -->
        <div class="flex-1 min-w-0">
          <p class="text-danger font-medium leading-snug"
             [class]="variant() === 'banner' ? 'text-sm' : 'text-xs'">
            {{ message() }}
          </p>

          @if (detail()) {
            <p class="text-danger/70 mt-1 leading-relaxed"
               [class]="variant() === 'banner' ? 'text-sm' : 'text-xs'">
              {{ detail() }}
            </p>
          }
        </div>

        <!-- Botón de retry — opcional -->
        @if (retryLabel()) {
          <app-button
            variant="ghost"
            size="sm"
            class="shrink-0"
            (click)="retry.emit()"
          >
            {{ retryLabel() }}
          </app-button>
        }

      </div>

    </div>
  `,
})
export class ErrorBannerComponent {
  message    = input.required<string>();
  detail     = input<string>('');
  retryLabel = input<string>('');
  variant    = input<ErrorBannerVariant>('banner');

  // output() — API moderna, reemplaza @Output() EventEmitter
  retry = output<void>();

  private readonly variantMap: Record<ErrorBannerVariant, string> = {
    banner: [
      'w-full rounded-xl p-4',
      'bg-danger-subtle border border-danger/20',
    ].join(' '),
    inline: [
      'w-full rounded-lg p-3',
      'bg-danger-subtle border border-danger/20',
    ].join(' '),
  };

  wrapperClasses = computed(() => this.variantMap[this.variant()]);
}
