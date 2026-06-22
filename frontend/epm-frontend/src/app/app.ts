import { Component, ChangeDetectionStrategy, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { SidebarComponent } from './shared/components/sidebar/sidebar.component';
import { NotificationStore } from './features/notifications/store/notification.store';

@Component({
  selector: 'app-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, SidebarComponent],
  template: `
    <div class="flex h-screen overflow-hidden" style="background: var(--color-bg-base);">
      <app-sidebar />
      <main class="relative flex-1 overflow-y-auto">
        <div class="pointer-events-none fixed inset-0 left-60"
             style="background:
               radial-gradient(ellipse 60% 40% at 70% 0%, color-mix(in oklch, var(--color-accent) 6%, transparent) 0%, transparent 60%),
               radial-gradient(ellipse 40% 30% at 100% 100%, color-mix(in oklch, var(--color-cyan) 4%, transparent) 0%, transparent 50%);
             z-index: 0; pointer-events: none;">
        </div>
        <div class="relative" style="z-index: 1; height: 100%;">
          <router-outlet />
        </div>
      </main>
    </div>
  `,
})
export class App implements OnInit {
  private readonly oauth = inject(OAuthService);
  private readonly notificationStore = inject(NotificationStore);

  ngOnInit(): void {
    // Cargar notificaciones y conectar WebSocket al iniciar la app
    this.notificationStore.loadNotifications();

    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    const userId = claims?.['sub'];
    const token  = this.oauth.getAccessToken();

    if (userId && token) {
      this.notificationStore.connectWebSocket(userId, token);
    }
  }
}
