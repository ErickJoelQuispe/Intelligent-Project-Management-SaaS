import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

export type SpinnerSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-spinner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="wrapperClasses()" role="status" aria-label="Loading">

      <!-- Spinner con gradiente cónico — mucho más visual que border-t -->
      <div [class]="ringClasses()" [style]="ringStyle()" aria-hidden="true">
        <!-- Inner circle que tapa el centro -->
        <div [class]="innerClasses()"></div>
      </div>

      @if (label()) {
        <span class="mt-4 text-sm font-medium tracking-wide"
              style="color: oklch(0.55 0.015 268); font-family: 'Outfit', sans-serif;">
          {{ label() }}
        </span>
      }

    </div>
  `,
})
export class SpinnerComponent {
  size  = input<SpinnerSize>('md');
  label = input<string>('');
  full  = input<boolean>(false);

  private readonly sizeMap: Record<SpinnerSize, { ring: string; inner: string; speed: string }> = {
    sm: { ring: 'size-6',  inner: 'size-4',  speed: '0.7s' },
    md: { ring: 'size-12', inner: 'size-9',  speed: '0.8s' },
    lg: { ring: 'size-16', inner: 'size-12', speed: '0.9s' },
  };

  ringClasses = computed(() =>
    `relative flex items-center justify-center rounded-full animate-spin ${this.sizeMap[this.size()].ring}`
  );

  innerClasses = computed(() =>
    `absolute rounded-full ${this.sizeMap[this.size()].inner}`  +
    ` bg-bg-base`
  );

  ringStyle = computed(() => {
    const s = this.sizeMap[this.size()];
    return `
      background: conic-gradient(
        from 0deg,
        oklch(0.65 0.26 285 / 0) 0%,
        oklch(0.65 0.26 285) 60%,
        oklch(0.78 0.18 200) 100%
      );
      animation-duration: ${s.speed};
      box-shadow: 0 0 16px oklch(0.65 0.26 285 / 0.4);
    `;
  });

  wrapperClasses = computed(() => {
    const base = 'flex flex-col items-center justify-center';
    return this.full() ? `${base} min-h-64` : `${base} p-6`;
  });
}
