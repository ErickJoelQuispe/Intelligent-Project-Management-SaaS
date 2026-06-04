import { Component, inject, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DatePipe } from '@angular/common';
import { NotificationStore } from '../../store/notification.store';
import { Notification } from '../../models/notification.model';

@Component({
  selector: 'app-notification-panel',
  standalone: true,
  imports: [MatCardModule, MatListModule, MatButtonModule, MatIconModule, DatePipe],
  template: `
    <mat-card class="notification-panel" data-testid="notification-panel">
      <mat-card-header>
        <mat-card-title>Notifications</mat-card-title>
        <span class="spacer"></span>
        @if (store.notifications().length > 0) {
          <button
            mat-button
            data-testid="mark-all-read-btn"
            (click)="onMarkAllAsRead()"
          >
            Mark all as read
          </button>
        }
        <button mat-icon-button (click)="closePanel.emit()" aria-label="Close panel">
          <mat-icon>close</mat-icon>
        </button>
      </mat-card-header>

      <mat-card-content>
        @if (store.notifications().length === 0) {
          <p class="empty-state" data-testid="empty-state">No notifications</p>
        } @else {
          <mat-list>
            @for (notification of store.notifications(); track notification.id) {
              <mat-list-item
                [class.unread]="!notification.read"
                [attr.data-testid]="'notification-item-' + notification.id"
              >
                <span matListItemTitle>{{ notification.message }}</span>
                <span matListItemLine class="timestamp">
                  {{ notification.createdAt | date:'short' }}
                </span>
                @if (!notification.read) {
                  <button
                    mat-button
                    matListItemMeta
                    [attr.data-testid]="'mark-read-btn-' + notification.id"
                    (click)="onMarkAsRead(notification)"
                  >
                    Mark read
                  </button>
                }
              </mat-list-item>
            }
          </mat-list>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .notification-panel {
      position: absolute;
      top: 100%;
      right: 0;
      width: 360px;
      max-height: 480px;
      overflow-y: auto;
      z-index: 200;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }

    mat-card-header {
      display: flex;
      align-items: center;
    }

    .spacer {
      flex: 1;
    }

    .unread {
      background-color: rgba(63, 81, 181, 0.05);
      font-weight: 600;
    }

    .empty-state {
      padding: 16px;
      text-align: center;
      color: rgba(0,0,0,0.54);
    }

    .timestamp {
      font-size: 0.75rem;
      color: rgba(0,0,0,0.54);
    }
  `],
})
export class NotificationPanelComponent {
  readonly store = inject(NotificationStore);
  readonly closePanel = output<void>();

  onMarkAsRead(notification: Notification): void {
    this.store.markAsRead(notification.id);
  }

  onMarkAllAsRead(): void {
    this.store.markAllAsRead();
  }
}
