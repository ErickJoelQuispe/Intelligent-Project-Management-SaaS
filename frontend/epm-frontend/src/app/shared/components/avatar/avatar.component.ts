import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
  signal,
} from '@angular/core';

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-avatar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="containerClasses()" [title]="name()">

      @if (src() && !imgError()) {
        <!-- Caso 1: imagen disponible y sin error de carga -->
        <img
          [src]="src()"
          [alt]="name()"
          class="size-full object-cover"
          (error)="onImgError()"
        />
      } @else if (initials()) {
        <!-- Caso 2: sin imagen pero hay nombre — mostramos iniciales -->
        <span [class]="textClasses()">{{ initials() }}</span>
      } @else {
        <!-- Caso 3: sin imagen ni nombre — ícono genérico -->
        <span class="material-symbols-rounded text-text-muted"
              [class]="iconSizeClasses()">
          person
        </span>
      }

    </div>
  `,
})
export class AvatarComponent {
  src  = input<string | null | undefined>(undefined);
  name = input<string>('');
  size = input<AvatarSize>('md');

  // signal local — se activa cuando la imagen falla al cargar
  // No es un input: es estado interno del componente
  imgError = signal(false);

  // Extraemos las iniciales del nombre
  // "Erick Quispe" → "EQ" | "Erick" → "E" | "" → null
  initials = computed(() => {
    const parts = this.name().trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return null;
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  });

  private readonly sizeMap: Record<AvatarSize, string> = {
    xs: 'size-6',
    sm: 'size-8',
    md: 'size-10',
    lg: 'size-14',
  };

  private readonly textSizeMap: Record<AvatarSize, string> = {
    xs: 'text-xs',
    sm: 'text-xs',
    md: 'text-sm',
    lg: 'text-base',
  };

  private readonly iconSizeMap: Record<AvatarSize, string> = {
    xs: 'text-sm',
    sm: 'text-base',
    md: 'text-xl',
    lg: 'text-2xl',
  };

  containerClasses = computed(() =>
    [
      'relative inline-flex items-center justify-center',
      'rounded-full overflow-hidden shrink-0',
      'bg-accent-subtle border border-border',
      this.sizeMap[this.size()],
    ].join(' ')
  );

  textClasses = computed(() =>
    ['font-semibold text-accent', this.textSizeMap[this.size()]].join(' ')
  );

  iconSizeClasses = computed(() => this.iconSizeMap[this.size()]);

  // Cuando la imagen falla, activamos el fallback
  // Este método es llamado desde el template con (error)
  onImgError(): void {
    this.imgError.set(true);
  }
}
