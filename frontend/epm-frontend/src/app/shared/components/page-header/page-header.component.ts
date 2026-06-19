import { Component, ChangeDetectionStrategy, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative animate-fade-up"
         style="border-bottom: 1px solid color-mix(in oklch, var(--color-border) 60%, transparent);">

      <!-- Línea gradiente arriba -->
      <div class="absolute top-0 left-0 right-0 h-px"
           style="background: linear-gradient(90deg, color-mix(in oklch, var(--color-accent) 40%, transparent) 0%, color-mix(in oklch, var(--color-cyan) 30%, transparent) 50%, transparent 100%);">
      </div>

      <div class="flex items-start justify-between gap-4 px-6 py-5">

        <div class="flex flex-col gap-1 min-w-0">
          <h1 class="font-semibold text-xl leading-tight truncate"
              style="font-family: 'Outfit', sans-serif; color: var(--color-text-primary);">
            {{ title() }}
          </h1>
          @if (description()) {
            <p class="text-sm" style="color: var(--color-text-secondary);">
              {{ description() }}
            </p>
          }
        </div>

        <div class="shrink-0 flex items-center gap-2">
          <ng-content select="[page-action]" />
        </div>

      </div>

      <ng-content select="[page-sub]" />
    </div>
  `,
})
export class PageHeaderComponent {
  title       = input.required<string>();
  description = input<string>('');
}
