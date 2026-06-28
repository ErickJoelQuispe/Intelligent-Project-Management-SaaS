import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  output,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { NotificationStore } from '../../store/notification.store';
import { NotificationItemComponent } from '../../../../shared/components/notification-item/notification-item.component';

import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { Notification } from '../../models/notification.model';

@Component({
  selector: 'app-notification-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NotificationItemComponent,
    SpinnerComponent,
    TranslocoPipe,
  ],
  template: `
    <div class="notif-panel animate-fade-up" data-testid="notification-panel" role="dialog" [attr.aria-label]="'notifications.panel.title' | transloco">

      <!-- Top accent line -->
      <div class="notif-panel-line" aria-hidden="true"></div>

      <!-- Header -->
      <header class="notif-panel-header">
        <div class="notif-panel-title-row">
          <span class="material-symbols-rounded notif-panel-title-icon" aria-hidden="true">notifications</span>
          <span class="notif-panel-title">{{ 'notifications.panel.title' | transloco }}</span>
          @if (store.unreadCount() > 0) {
            <span class="notif-unread-pill" aria-label="{{ store.unreadCount() }} unread">
              {{ store.unreadCount() }}
            </span>
          }
        </div>

        <div class="notif-panel-actions">
          @if (store.notifications().length > 0) {
            <button
              class="notif-mark-all-btn"
              data-testid="mark-all-read-btn"
              (click)="store.markAllAsRead()"
              [attr.aria-label]="'notifications.panel.markAllAriaLabel' | transloco"
            >
              <span class="material-symbols-rounded" aria-hidden="true">done_all</span>
              {{ 'notifications.panel.markAllRead' | transloco }}
            </button>
          }
          <button
            class="notif-close-btn"
            (click)="closePanel.emit()"
            [attr.aria-label]="'notifications.panel.close' | transloco"
          >
            <span class="material-symbols-rounded" aria-hidden="true">close</span>
          </button>
        </div>
      </header>

      <!-- Divider -->
      <div class="notif-panel-divider" aria-hidden="true"></div>

      <!-- Content -->
      <div class="notif-panel-body" role="list">
        @if (store.loading()) {
          <div class="notif-loading">
            <app-spinner size="sm" />
          </div>

        } @else if (store.notifications().length === 0) {
          <div class="notif-empty">
            <span class="material-symbols-rounded notif-empty-icon" aria-hidden="true">notifications_off</span>
            <span class="notif-empty-title">{{ 'notifications.panel.allCaughtUp' | transloco }}</span>
            <span class="notif-empty-desc">{{ 'notifications.panel.noNotifications' | transloco }}</span>
          </div>

        } @else {

          <!-- Unread section -->
          @if (unread().length > 0) {
            <div class="notif-section-label" [attr.aria-label]="'notifications.panel.unread' | transloco">
              <span>{{ 'notifications.panel.unread' | transloco }}</span>
              <span class="notif-section-count">{{ unread().length }}</span>
            </div>
            @for (n of unread(); track n.id) {
              <app-notification-item
                [notification]="n"
                [attr.data-testid]="'notification-item-' + n.id"
                (markRead)="store.markAsRead($event)"
                role="listitem"
              />
            }
          }

          <!-- Read section -->
          @if (read().length > 0) {
            <div class="notif-section-label notif-section-label--read" [attr.aria-label]="'notifications.panel.earlier' | transloco">
              <span>{{ 'notifications.panel.earlier' | transloco }}</span>
            </div>
            @for (n of read(); track n.id) {
              <app-notification-item
                [notification]="n"
                [attr.data-testid]="'notification-item-' + n.id"
                (markRead)="store.markAsRead($event)"
                role="listitem"
              />
            }
          }

        }
      </div>

    </div>

    <style>
      /* ── Panel shell ─────────────────────── */
      .notif-panel {
        position: relative;
        width: 24rem;
        border-radius: 0.875rem;
        overflow: hidden;
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border-strong);
        box-shadow:
          var(--shadow-lg),
          0 0 0 1px color-mix(in oklch, var(--color-accent) 8%, transparent);
      }

      /* ── Top accent line ─────────────────── */
      .notif-panel-line {
        height: 2.5px;
        background: linear-gradient(
          90deg,
          var(--color-accent) 0%,
          var(--color-cyan) 50%,
          transparent 100%
        );
      }

      /* ── Header ──────────────────────────── */
      .notif-panel-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        padding: 0.875rem 1rem 0.75rem;
      }

      .notif-panel-title-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }

      .notif-panel-title-icon {
        font-size: 1.125rem;
        color: var(--color-accent);
      }

      .notif-panel-title {
        font-family: 'Outfit', sans-serif;
        font-size: 0.9375rem;
        font-weight: 650;
        color: var(--color-text-primary);
        letter-spacing: -0.01em;
      }

      .notif-unread-pill {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 1.25rem;
        height: 1.25rem;
        padding: 0 0.3rem;
        border-radius: 9999px;
        background: var(--color-accent);
        color: white;
        font-size: 0.6875rem;
        font-weight: 700;
        font-family: 'Outfit', sans-serif;
        line-height: 1;
      }

      .notif-panel-actions {
        display: flex;
        align-items: center;
        gap: 0.25rem;
      }

      .notif-mark-all-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        padding: 0.3125rem 0.625rem;
        border-radius: 0.5rem;
        background: transparent;
        border: none;
        cursor: pointer;
        font-size: 0.75rem;
        font-weight: 500;
        color: var(--color-text-muted);
        transition: background 0.15s ease, color 0.15s ease;
        white-space: nowrap;

        .material-symbols-rounded { font-size: 0.875rem; }

        &:hover {
          background: color-mix(in oklch, var(--color-bg-overlay) 80%, transparent);
          color: var(--color-text-primary);
        }
        &:focus-visible {
          outline: 2px solid var(--color-accent);
          outline-offset: 1px;
        }
      }

      .notif-close-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.5rem;
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--color-text-muted);
        transition: background 0.15s ease, color 0.15s ease;

        .material-symbols-rounded { font-size: 1.125rem; }

        &:hover {
          background: color-mix(in oklch, var(--color-bg-overlay) 80%, transparent);
          color: var(--color-text-primary);
        }
        &:focus-visible {
          outline: 2px solid var(--color-accent);
          outline-offset: 1px;
        }
      }

      /* ── Divider ─────────────────────────── */
      .notif-panel-divider {
        height: 1px;
        background: color-mix(in oklch, var(--color-border) 60%, transparent);
        margin: 0;
      }

      /* ── Body / list ─────────────────────── */
      .notif-panel-body {
        max-height: 26rem;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: var(--color-border) transparent;

        &::-webkit-scrollbar { width: 3px; }
        &::-webkit-scrollbar-track { background: transparent; }
        &::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 9999px; }
      }

      /* ── Section labels ──────────────────── */
      .notif-section-label {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.5rem 1rem 0.25rem;
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.07em;
        text-transform: uppercase;
        color: var(--color-accent);
        font-family: 'JetBrains Mono', monospace;

        &--read {
          color: var(--color-text-disabled);
          border-top: 1px solid color-mix(in oklch, var(--color-border) 40%, transparent);
          margin-top: 0.25rem;
          padding-top: 0.625rem;
        }
      }

      .notif-section-count {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.25rem;
        height: 1.25rem;
        border-radius: 9999px;
        background: color-mix(in oklch, var(--color-accent) 15%, transparent);
        color: var(--color-accent);
        font-size: 0.625rem;
        font-weight: 800;
      }

      /* ── Loading ─────────────────────────── */
      .notif-loading {
        display: flex;
        justify-content: center;
        padding: 2rem 0;
      }

      /* ── Empty state ─────────────────────── */
      .notif-empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.5rem;
        padding: 3rem 1rem;
        text-align: center;
      }

      .notif-empty-icon {
        font-size: 2.5rem;
        color: var(--color-text-disabled);
        opacity: 0.5;
      }

      .notif-empty-title {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--color-text-secondary);
        font-family: 'Outfit', sans-serif;
      }

      .notif-empty-desc {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
      }
    </style>
  `,
})
export class NotificationPanelComponent {
  readonly store      = inject(NotificationStore);
  readonly closePanel = output<void>();

  unread = computed(() => this.store.notifications().filter((n: Notification) => !n.read));
  read   = computed(() => this.store.notifications().filter((n: Notification) => n.read));
}
