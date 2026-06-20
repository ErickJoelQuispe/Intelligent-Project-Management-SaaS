import { Component, ChangeDetectionStrategy, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header animate-fade-up" role="banner">

      <!-- Top accent line -->
      <div class="page-header-line" aria-hidden="true"></div>

      <div class="page-header-inner">

        <!-- Title block -->
        <div class="page-header-title-block">
          <h1 class="page-header-title">{{ title() }}</h1>
          @if (description()) {
            <p class="page-header-desc">{{ description() }}</p>
          }
        </div>

        <!-- Actions slot -->
        <div class="page-header-actions">
          <ng-content select="[page-action]" />
        </div>

      </div>

      <!-- Sub-slot (filters, tabs, etc.) -->
      <ng-content select="[page-sub]" />

    </div>

    <style>
      .page-header {
        position: relative;
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 55%, transparent);
      }

      .page-header-line {
        position: absolute;
        top: 0; left: 0; right: 0;
        height: 1px;
        background: linear-gradient(
          90deg,
          color-mix(in oklch, var(--color-accent) 50%, transparent) 0%,
          color-mix(in oklch, var(--color-cyan) 40%, transparent) 45%,
          transparent 75%
        );
      }

      .page-header-inner {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 1.5rem 1.75rem 1.375rem;
      }

      .page-header-title-block {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        min-width: 0;
      }

      .page-header-title {
        font-family: 'Outfit', sans-serif;
        font-size: 1.5rem;
        font-weight: 700;
        letter-spacing: -0.02em;
        line-height: 1.2;
        color: var(--color-text-primary);
        margin: 0;
      }

      .page-header-desc {
        font-size: 0.875rem;
        line-height: 1.5;
        color: var(--color-text-muted);
        margin: 0;
        font-weight: 400;
      }

      .page-header-actions {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-shrink: 0;
      }
    </style>
  `,
})
export class PageHeaderComponent {
  title       = input.required<string>();
  description = input<string>('');
}
