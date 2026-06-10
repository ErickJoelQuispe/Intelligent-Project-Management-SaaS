import {
  Component,
  ChangeDetectionStrategy,
  inject,
  output,
} from '@angular/core';
import { NotificationStore } from '../../store/notification.store';
import { NotificationItemComponent } from '../../../../shared/components/notification-item/notification-item.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';

@Component({
  selector: 'app-notification-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NotificationItemComponent,
    ButtonComponent,
    EmptyStateComponent,
    SpinnerComponent,
  ],
  template: `
      <!-- Panel flotante — posicionado via fixed desde el bell -->
      <div
        data-testid="notification-panel"
        class="w-96 rounded-xl overflow-hidden animate-fade-up"
        style="background: oklch(0.13 0.025 268);
               border: 1px solid oklch(0.28 0.022 268);
               box-shadow: 0 8px 32px oklch(0 0 0 / 0.7),
                           0 0 0 1px oklch(0.65 0.26 285 / 0.08);"
      >

      <!-- Header del panel -->
      <div class="flex items-center justify-between
                  px-4 py-3 border-b border-border">
        <span class="text-text-primary text-sm font-semibold">
          Notifications
        </span>
        <div class="flex items-center gap-1">
          @if (store.notifications().length > 0) {
            <app-button
              variant="ghost"
              size="sm"
              data-testid="mark-all-read-btn"
              (click)="store.markAllAsRead()"
            >
              Mark all read
            </app-button>
          }
          <button
            (click)="closePanel.emit()"
            aria-label="Close notifications"
            class="flex items-center justify-center size-7 rounded-lg
                   text-text-muted hover:text-text-primary hover:bg-bg-overlay
                   transition-colors duration-150 cursor-pointer"
          >
            <span class="material-symbols-rounded text-base">close</span>
          </button>
        </div>
      </div>

      <!-- Lista de notificaciones -->
      <div class="max-h-96 overflow-y-auto">
        @if (store.loading()) {
          <app-spinner size="sm" />
        } @else if (store.notifications().length === 0) {
          <app-empty-state
            icon="notifications"
            title="You're all caught up"
            description="No new notifications."
            size="sm"
            data-testid="empty-state"
          />
        } @else {
          @for (notification of store.notifications(); track notification.id) {
            <app-notification-item
              [notification]="notification"
              [attr.data-testid]="'notification-item-' + notification.id"
              (markRead)="store.markAsRead($event)"
            />
          }
        }
      </div>

    </div>
  `,
})
export class NotificationPanelComponent {
  readonly store      = inject(NotificationStore);
  readonly closePanel = output<void>();
}
