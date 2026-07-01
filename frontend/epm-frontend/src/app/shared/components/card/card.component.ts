import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

export type CardVariant = 'default' | 'elevated' | 'flat' | 'surface';
export type CardPadding = 'none' | 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="cardClasses()">

      <!-- Header — solo renderiza si el padre proyecta algo con [card-header] -->
      <ng-content select="[card-header]" />

      <!-- Divider entre header y body — solo si hay header -->
      <!-- Tailwind no puede hacer esto condicionalmente sin lógica,
           así que el padre es responsable de incluir el divider si lo necesita -->

      <!-- Contenido principal — proyecta TODO lo que no tenga slot específico -->
      <div [class]="bodyClasses()">
        <ng-content />
      </div>

      <!-- Footer — solo renderiza si el padre proyecta algo con [card-footer] -->
      <ng-content select="[card-footer]" />

    </div>
  `,
})
export class CardComponent {
  variant = input<CardVariant>('default');
  padding = input<CardPadding>('md');

  // noPadding en el body — útil cuando el contenido tiene su propio padding
  // (tablas, listas que necesitan ir de borde a borde)
  noPadding = input<boolean>(false);

  private readonly variantMap: Record<CardVariant, string> = {
    default:  'glass shadow-md',
    elevated: 'bg-bg-elevated border border-border-strong shadow-lg',
    flat:     'bg-bg-surface border border-transparent',
    surface:  'bg-bg-surface border border-border',
  };

  private readonly paddingMap: Record<CardPadding, string> = {
    none: 'p-0',
    sm:   'p-4',
    md:   'p-6',
    lg:   'p-8',
  };

  cardClasses = computed(() =>
    [
      'rounded-xl overflow-hidden',
      this.variantMap[this.variant()],
    ].join(' ')
  );

  // El padding va en el body, no en el card wrapper
  // Así el header y footer pueden ir de borde a borde si quieren
  bodyClasses = computed(() =>
    this.noPadding() ? '' : this.paddingMap[this.padding()]
  );
}
