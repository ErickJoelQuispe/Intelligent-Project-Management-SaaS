import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { ButtonComponent } from '../button/button.component';
import { Notification } from '../../../features/notifications/models/notification.model';

// Mapa de tipo de notificación → ícono de Material Symbols
const TYPE_ICON: Record<string, string> = {
  TASK_CREATED:            'task_alt',
  TASK_ASSIGNED:           'person_add',
  TASK_STATUS_CHANGED:     'swap_horiz',
  TASK_DELETED:            'delete',
  PROJECT_CREATED:         'folder',
  PROJECT_ARCHIVED:        'archive',
  TEAM_ASSIGNED_TO_PROJECT:'group',
  MEMBER_JOINED_TEAM:      'person',
  MEMBER_LEFT_TEAM:        'person_remove',
};

@Component({
  selector: 'app-notification-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, DatePipe],
  template: `
    <!-- El fondo cambia sutilmente si la notificación no fue leída -->
    <div [class]="wrapperClasses()">

      <div class="flex items-start gap-3 min-w-0">

        <!-- Ícono de tipo en contenedor cuadrado fijo -->
        <div class="relative shrink-0 mt-0.5">
          <!-- Dot unread — esquina superior derecha del ícono -->
          @if (!notification().read) {
            <span class="absolute -top-0.5 -right-0.5 size-2 rounded-full bg-accent
                         ring-2 ring-bg-elevated z-10">
            </span>
          }
          <div class="size-8 rounded-lg flex items-center justify-center"
               style="background: oklch(0.17 0.025 268);">
            <span class="material-symbols-rounded text-base"
                  style="color: oklch(0.65 0.26 285 / 0.8); font-size: 1rem;">
              {{ icon() }}
            </span>
          </div>
        </div>

        <!-- Contenido -->
        <div class="flex-1 min-w-0">
          <p class="text-sm leading-snug"
             [class]="notification().read
               ? 'text-text-secondary'
               : 'text-text-primary font-medium'">
            {{ notification().message }}
          </p>
          <time class="text-xs mt-0.5 block"
                style="color: oklch(0.42 0.012 268);"
                [dateTime]="notification().createdAt">
            {{ notification().createdAt | date: 'MMM d, h:mm a' }}
          </time>
        </div>

      </div>

      <!-- Acción mark read — solo si no fue leída -->
      @if (!notification().read) {
        <app-button
          variant="ghost"
          size="sm"
          class="shrink-0 self-center"
          (click)="onMarkRead()"
        >
          Mark read
        </app-button>
      }

    </div>
  `,
})
export class NotificationItemComponent {
  notification = input.required<Notification>();

  // El padre recibe el id — no el objeto completo
  // El componente no decide qué hacer con él, solo avisa
  markRead = output<string>();

  icon = computed(() =>
    TYPE_ICON[this.notification().type] ?? 'notifications'
  );

  wrapperClasses = computed(() => {
    const base = 'flex items-start justify-between gap-3 px-4 py-3 ' +
                 'transition-colors duration-150 hover:bg-bg-overlay';
    // Fondo levemente distinto para no leídas — sin hardcodear colores
    return this.notification().read
      ? base
      : `${base} bg-accent-subtle/40`;
  });

  onMarkRead(): void {
    this.markRead.emit(this.notification().id);
  }
}
