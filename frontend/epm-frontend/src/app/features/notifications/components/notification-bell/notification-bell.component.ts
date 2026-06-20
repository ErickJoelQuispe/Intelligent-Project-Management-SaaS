import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { NotificationStore } from '../../store/notification.store';
import { NotificationPanelComponent } from '../notification-panel/notification-panel.component';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NotificationPanelComponent],
  template: `
    <div class="bell-host" #bellRef>

      <button
        (click)="togglePanel()"
        data-testid="bell-button"
        class="bell-btn"
        [class.bell-btn--active]="panelOpen()"
        [class.bell-btn--has-unread]="store.unreadCount() > 0"
        [attr.aria-label]="store.unreadCount() > 0
          ? store.unreadCount() + ' unread notifications'
          : 'Notifications'"
        [attr.aria-expanded]="panelOpen()"
      >
        <span class="material-symbols-rounded bell-icon" aria-hidden="true">
          {{ store.unreadCount() > 0 ? 'notifications_active' : 'notifications' }}
        </span>

        @if (store.unreadCount() > 0) {
          <span
            class="bell-badge"
            data-testid="bell-badge"
            aria-hidden="true"
          >
            {{ store.unreadCount() > 99 ? '99+' : store.unreadCount() }}
          </span>
        }
      </button>

      @if (panelOpen()) {
        <div class="bell-overlay" (click)="panelOpen.set(false)" aria-hidden="true"></div>

        <div class="bell-panel-anchor"
             [style.left]="panelLeft"
             [style.top]="panelTop">
          <app-notification-panel (closePanel)="panelOpen.set(false)" />
        </div>
      }

    </div>

    <style>
      .bell-host {
        position: relative;
        display: inline-flex;
        align-items: center;
      }

      /* ── Bell button ─────────────────────── */
      .bell-btn {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 2.25rem;
        height: 2.25rem;
        border-radius: 0.625rem;
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--color-sidebar-text);
        transition: background 0.15s ease, color 0.15s ease;
        outline-offset: 2px;
      }
      .bell-btn:hover {
        background: color-mix(in oklch, var(--color-bg-overlay) 80%, transparent);
        color: var(--color-text-primary);
      }
      .bell-btn:focus-visible {
        outline: 2px solid var(--color-accent);
      }
      .bell-btn--active {
        background: color-mix(in oklch, var(--color-accent) 10%, transparent);
        color: var(--color-accent);
      }
      .bell-btn--has-unread .bell-icon {
        animation: bell-shake 2.5s ease-in-out 0.5s infinite;
      }

      @keyframes bell-shake {
        0%, 85%, 100% { transform: rotate(0deg); }
        88%  { transform: rotate(12deg); }
        92%  { transform: rotate(-10deg); }
        96%  { transform: rotate(8deg); }
        98%  { transform: rotate(-6deg); }
      }

      .bell-icon { font-size: 1.25rem; }

      /* ── Badge ───────────────────────────── */
      .bell-badge {
        position: absolute;
        top: 0.25rem;
        right: 0.25rem;
        min-width: 1rem;
        height: 1rem;
        padding: 0 0.25rem;
        border-radius: 9999px;
        background: var(--color-danger);
        color: white;
        font-size: 0.6rem;
        font-weight: 800;
        line-height: 1rem;
        text-align: center;
        font-family: 'Outfit', sans-serif;
        pointer-events: none;
        box-shadow: 0 0 0 2px var(--color-sidebar-bg);
      }

      /* ── Overlay + panel anchor ──────────── */
      .bell-overlay {
        position: fixed;
        inset: 0;
        z-index: 40;
      }

      .bell-panel-anchor {
        position: fixed;
        z-index: 50;
      }
    </style>
  `,
})
export class NotificationBellComponent {
  readonly store     = inject(NotificationStore);
  readonly panelOpen = signal(false);

  @ViewChild('bellRef') bellRef!: ElementRef<HTMLElement>;

  private readonly PANEL_HEIGHT = 460;
  private readonly PANEL_WIDTH  = 384;

  get panelLeft(): string {
    if (!this.bellRef) return '15rem';
    const rect = this.bellRef.nativeElement.getBoundingClientRect();
    return `${rect.right + 8}px`;
  }

  get panelTop(): string {
    if (!this.bellRef) return '8px';
    const rect   = this.bellRef.nativeElement.getBoundingClientRect();
    const vh     = window.innerHeight;
    const ideal  = rect.top;
    const maxTop = vh - this.PANEL_HEIGHT - 8;
    return `${Math.min(ideal, maxTop)}px`;
  }

  togglePanel(): void {
    this.panelOpen.update(v => !v);
  }
}
