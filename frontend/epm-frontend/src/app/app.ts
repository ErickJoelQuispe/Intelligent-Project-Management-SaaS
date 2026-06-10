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
    <div class="flex h-screen overflow-hidden" style="background: oklch(0.07 0.02 268);">
      <app-sidebar />
      <main class="relative flex-1 overflow-y-auto">
        <div class="pointer-events-none fixed inset-0 left-60"
             style="background:
               radial-gradient(ellipse 60% 40% at 70% 0%, oklch(0.65 0.26 285 / 0.06) 0%, transparent 60%),
               radial-gradient(ellipse 40% 30% at 100% 100%, oklch(0.78 0.18 200 / 0.04) 0%, transparent 50%);
             z-index: 0; pointer-events: none;">
        </div>
        <div class="relative" style="z-index: 1;">
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
