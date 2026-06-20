import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Notification } from '../../../features/notifications/models/notification.model';

const TYPE_ICON: Record<string, string> = {
  TASK_CREATED:             'task_alt',
  TASK_ASSIGNED:            'person_add',
  TASK_STATUS_CHANGED:      'swap_horiz',
  TASK_DELETED:             'delete',
  PROJECT_CREATED:          'folder',
  PROJECT_ARCHIVED:         'inventory_2',
  TEAM_ASSIGNED_TO_PROJECT: 'group',
  MEMBER_JOINED_TEAM:       'person_add',
  MEMBER_LEFT_TEAM:         'person_remove',
};

/** Semantic color group per notification type */
const TYPE_COLOR: Record<string, 'accent' | 'cyan' | 'success' | 'warning' | 'danger'> = {
  TASK_CREATED:             'accent',
  TASK_ASSIGNED:            'accent',
  TASK_STATUS_CHANGED:      'cyan',
  TASK_DELETED:             'danger',
  PROJECT_CREATED:          'cyan',
  PROJECT_ARCHIVED:         'warning',
  TEAM_ASSIGNED_TO_PROJECT: 'success',
  MEMBER_JOINED_TEAM:       'success',
  MEMBER_LEFT_TEAM:         'warning',
};

@Component({
  selector: 'app-notification-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    <div
      class="notif-item"
      [class.notif-item--unread]="!notification().read"
      (click)="!notification().read && onMarkRead()"
      [attr.role]="!notification().read ? 'button' : null"
      [attr.tabindex]="!notification().read ? 0 : null"
      [attr.aria-label]="!notification().read ? 'Mark as read: ' + notification().message : null"
      (keydown.enter)="!notification().read && onMarkRead()"
      (keydown.space)="!notification().read && onMarkRead()"
    >

      <!-- Left: icon container -->
      <div class="notif-item-icon-wrap" [attr.data-color]="colorKey()">
        <span class="material-symbols-rounded notif-item-icon" aria-hidden="true">
          {{ icon() }}
        </span>
        @if (!notification().read) {
          <span class="notif-item-dot" aria-hidden="true"></span>
        }
      </div>

      <!-- Center: content -->
      <div class="notif-item-body">
        <p class="notif-item-message" [class.notif-item-message--unread]="!notification().read">
          {{ notification().message }}
        </p>
        <time
          class="notif-item-time"
          [dateTime]="notification().createdAt"
        >
          {{ notification().createdAt | date: 'MMM d · h:mm a' }}
        </time>
      </div>

      <!-- Right: unread indicator -->
      @if (!notification().read) {
        <div class="notif-item-unread-bar" aria-hidden="true"></div>
      }

    </div>

    <style>
      /* ── Item shell ──────────────────────── */
      .notif-item {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 40%, transparent);
        transition: background 0.12s ease;
        cursor: default;

        &:last-child { border-bottom: none; }

        &--unread {
          background: color-mix(in oklch, var(--color-accent) 4%, transparent);
          cursor: pointer;

          &:hover {
            background: color-mix(in oklch, var(--color-accent) 7%, transparent);
          }
        }

        &:not(.notif-item--unread):hover {
          background: color-mix(in oklch, var(--color-bg-overlay) 60%, transparent);
        }
      }

      /* ── Icon container ──────────────────── */
      .notif-item-icon-wrap {
        position: relative;
        flex-shrink: 0;
        width: 2.25rem;
        height: 2.25rem;
        border-radius: 0.625rem;
        display: flex;
        align-items: center;
        justify-content: center;
        background: color-mix(in oklch, var(--color-bg-elevated) 80%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        margin-top: 0.0625rem;
        transition: background 0.15s ease;

        /* Color variants */
        &[data-color="accent"] {
          background: color-mix(in oklch, var(--color-accent) 12%, var(--color-bg-elevated));
          border-color: color-mix(in oklch, var(--color-accent) 20%, transparent);
          .notif-item-icon { color: var(--color-accent); }
        }
        &[data-color="cyan"] {
          background: color-mix(in oklch, var(--color-cyan) 12%, var(--color-bg-elevated));
          border-color: color-mix(in oklch, var(--color-cyan) 20%, transparent);
          .notif-item-icon { color: var(--color-cyan); }
        }
        &[data-color="success"] {
          background: color-mix(in oklch, var(--color-success) 12%, var(--color-bg-elevated));
          border-color: color-mix(in oklch, var(--color-success) 20%, transparent);
          .notif-item-icon { color: var(--color-success); }
        }
        &[data-color="warning"] {
          background: color-mix(in oklch, var(--color-warning) 12%, var(--color-bg-elevated));
          border-color: color-mix(in oklch, var(--color-warning) 20%, transparent);
          .notif-item-icon { color: var(--color-warning); }
        }
        &[data-color="danger"] {
          background: color-mix(in oklch, var(--color-danger) 12%, var(--color-bg-elevated));
          border-color: color-mix(in oklch, var(--color-danger) 20%, transparent);
          .notif-item-icon { color: var(--color-danger); }
        }
      }

      .notif-item-icon { font-size: 1rem; }

      /* Unread dot on icon */
      .notif-item-dot {
        position: absolute;
        top: -0.1875rem;
        right: -0.1875rem;
        width: 0.5rem;
        height: 0.5rem;
        border-radius: 50%;
        background: var(--color-accent);
        border: 1.5px solid var(--color-bg-surface);
        animation: glow-pulse 2s ease-in-out infinite;
      }

      /* ── Content ─────────────────────────── */
      .notif-item-body {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }

      .notif-item-message {
        font-size: 0.8125rem;
        line-height: 1.45;
        color: var(--color-text-secondary);
        margin: 0;

        &--unread {
          color: var(--color-text-primary);
          font-weight: 500;
        }
      }

      .notif-item-time {
        font-size: 0.6875rem;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
        font-feature-settings: 'tnum';
      }

      /* ── Unread accent bar (right edge) ──── */
      .notif-item-unread-bar {
        flex-shrink: 0;
        align-self: stretch;
        width: 2.5px;
        border-radius: 9999px;
        background: var(--color-accent);
        opacity: 0.6;
        margin: 0.25rem 0;
      }
    </style>
  `,
})
export class NotificationItemComponent {
  notification = input.required<Notification>();
  markRead     = output<string>();

  icon     = computed(() => TYPE_ICON[this.notification().type]  ?? 'notifications');
  colorKey = computed(() => TYPE_COLOR[this.notification().type] ?? 'accent');

  onMarkRead(): void {
    this.markRead.emit(this.notification().id);
  }
}
