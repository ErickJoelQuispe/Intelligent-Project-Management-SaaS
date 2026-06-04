import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { NotificationStore } from '../../store/notification.store';
import { NotificationPanelComponent } from '../notification-panel/notification-panel.component';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, NotificationPanelComponent],
  template: `
    <div class="notification-wrapper">
      <button
        mat-icon-button
        data-testid="bell-button"
        aria-label="Notifications"
        (click)="togglePanel()"
      >
        <mat-icon>notifications</mat-icon>
      </button>
      @if (store.unreadCount() > 0) {
        <span
          class="badge"
          data-testid="bell-badge"
          aria-label="Unread notifications"
        >{{ store.unreadCount() }}</span>
      }

      @if (panelOpen()) {
        <app-notification-panel
          (closePanel)="panelOpen.set(false)"
        />
      }
    </div>
  `,
  styles: [`
    .notification-wrapper {
      position: relative;
      display: inline-flex;
      align-items: center;
    }

    .badge {
      position: absolute;
      top: 4px;
      right: 4px;
      background: #f44336;
      color: white;
      border-radius: 50%;
      width: 18px;
      height: 18px;
      font-size: 11px;
      display: flex;
      align-items: center;
      justify-content: center;
      pointer-events: none;
    }
  `],
})
export class NotificationBellComponent {
  readonly store = inject(NotificationStore);
  readonly panelOpen = signal(false);

  togglePanel(): void {
    this.panelOpen.update((v) => !v);
  }
}
