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
    <div class="relative inline-flex items-center" #bellRef>

      <button
        (click)="togglePanel()"
        data-testid="bell-button"
        aria-label="Notifications"
        class="relative flex items-center justify-center
               size-9 rounded-lg
               text-sidebar-text hover:text-text-primary hover:bg-bg-overlay
               transition-colors duration-150 cursor-pointer"
      >
        <span class="material-symbols-rounded text-xl">notifications</span>

        @if (store.unreadCount() > 0) {
          <span
            data-testid="bell-badge"
            aria-label="Unread notifications"
            class="absolute top-1 right-1
                   min-w-4 h-4 px-1
                   rounded-full bg-danger text-white
                   text-xs font-bold leading-4 text-center
                   pointer-events-none"
          >
            {{ store.unreadCount() > 99 ? '99+' : store.unreadCount() }}
          </span>
        }
      </button>

      @if (panelOpen()) {
        <!-- Overlay invisible para cerrar al hacer click fuera -->
        <div class="fixed inset-0 z-40" (click)="panelOpen.set(false)"></div>

        <!-- Panel con position fixed — escapa del overflow:hidden del sidebar -->
        <div class="fixed z-50"
             [style.left]="panelLeft"
             [style.top]="panelTop">
          <app-notification-panel (closePanel)="panelOpen.set(false)" />
        </div>
      }

    </div>
  `,
})
export class NotificationBellComponent {
  readonly store     = inject(NotificationStore);
  readonly panelOpen = signal(false);

  @ViewChild('bellRef') bellRef!: ElementRef<HTMLElement>;

  private readonly PANEL_HEIGHT = 420; // altura estimada del panel
  private readonly PANEL_WIDTH  = 384; // w-96

  get panelLeft(): string {
    if (!this.bellRef) return '15rem';
    const rect = this.bellRef.nativeElement.getBoundingClientRect();
    return `${rect.right + 8}px`;
  }

  get panelTop(): string {
    if (!this.bellRef) return '8px';
    const rect   = this.bellRef.nativeElement.getBoundingClientRect();
    const vh     = window.innerHeight;
    // Alinear el top del panel con el bell, pero si se sale del viewport por abajo → subir
    const ideal  = rect.top;
    const maxTop = vh - this.PANEL_HEIGHT - 8;
    return `${Math.min(ideal, maxTop)}px`;
  }

  togglePanel(): void {
    this.panelOpen.update(v => !v);
  }
}
