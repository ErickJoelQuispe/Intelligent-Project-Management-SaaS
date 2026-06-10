import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

export type EmptyStateSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="wrapperClasses()">

      @if (icon()) {
        <!-- Contenedor del ícono con aura de acento -->
        <div class="relative flex items-center justify-center mb-5">
          <!-- Aura difusa detrás del ícono -->
          <div class="absolute rounded-full"
               [class]="auraClasses()"
               style="background: oklch(0.65 0.26 285 / 0.12); filter: blur(16px);">
          </div>
          <!-- Contenedor del ícono -->
          <div class="relative flex items-center justify-center rounded-2xl"
               [class]="iconContainerSizeClasses()"
               style="background: oklch(0.11 0.025 268 / 0.8);
                      border: 1px solid oklch(0.65 0.26 285 / 0.2);
                      box-shadow: 0 0 20px oklch(0.65 0.26 285 / 0.1), inset 0 1px 0 oklch(1 0 0 / 0.04);">
            <span class="material-symbols-rounded"
                  [class]="iconSizeClasses()"
                  style="color: oklch(0.65 0.26 285 / 0.7);">
              {{ icon() }}
            </span>
          </div>
        </div>
      }

      <h3 [class]="titleClasses()" style="font-family: 'Outfit', sans-serif;">
        {{ title() }}
      </h3>

      @if (description()) {
        <p class="text-center max-w-xs leading-relaxed mt-1"
           [class]="descriptionSizeClasses()"
           style="color: oklch(0.42 0.012 268);">
          {{ description() }}
        </p>
      }

      <div class="mt-4">
        <ng-content select="[action]" />
      </div>

    </div>
  `,
})
export class EmptyStateComponent {
  icon        = input<string>('inbox');
  title       = input<string>('Nothing here yet');
  description = input<string>('');
  size        = input<EmptyStateSize>('md');

  private readonly wrapperMap: Record<EmptyStateSize, string> = {
    sm: 'flex flex-col items-center justify-center py-10 px-4',
    md: 'flex flex-col items-center justify-center py-20 px-4',
    lg: 'flex flex-col items-center justify-center py-28 px-4',
  };

  private readonly iconContainerMap: Record<EmptyStateSize, string> = {
    sm: 'size-12',
    md: 'size-16',
    lg: 'size-20',
  };

  private readonly auraMap: Record<EmptyStateSize, string> = {
    sm: 'size-16',
    md: 'size-24',
    lg: 'size-32',
  };

  private readonly iconSizeMap: Record<EmptyStateSize, string> = {
    sm: 'text-2xl',
    md: 'text-3xl',
    lg: 'text-4xl',
  };

  private readonly titleMap: Record<EmptyStateSize, string> = {
    sm: 'text-sm  font-semibold',
    md: 'text-base font-semibold',
    lg: 'text-lg  font-semibold',
  };

  private readonly descriptionMap: Record<EmptyStateSize, string> = {
    sm: 'text-xs',
    md: 'text-sm',
    lg: 'text-sm',
  };

  wrapperClasses           = computed(() => this.wrapperMap[this.size()]);
  iconContainerSizeClasses = computed(() => this.iconContainerMap[this.size()]);
  auraClasses              = computed(() => this.auraMap[this.size()]);
  iconSizeClasses          = computed(() => this.iconSizeMap[this.size()]);
  titleClasses             = computed(() => this.titleMap[this.size()] + ' text-text-primary');
  descriptionSizeClasses   = computed(() => this.descriptionMap[this.size()]);
}
