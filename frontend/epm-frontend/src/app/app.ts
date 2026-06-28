import { Component, ChangeDetectionStrategy, OnInit, inject, effect } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { TranslocoService } from '@jsverse/transloco';
import { SidebarComponent } from './shared/components/sidebar/sidebar.component';
import { NotificationStore } from './features/notifications/store/notification.store';
import { ProfileStore } from './features/settings/store/profile.store';
import { ThemeService } from './core/theme/theme.service';

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
  private readonly oauth              = inject(OAuthService);
  private readonly notificationStore  = inject(NotificationStore);
  private readonly profileStore       = inject(ProfileStore);
  private readonly translocoService   = inject(TranslocoService);
  // Inject ThemeService at root level to force initialization on bootstrap —
  // without this, the service constructor (which applies data-theme) is deferred
  // until the first component that uses it, causing a flash of unstyled content.
  private readonly _theme = inject(ThemeService);

  constructor() {
    // Reactively sync the user's preferred language from their profile to Transloco.
    // This runs after ProfileStore emits a loaded profile and handles the race
    // between localStorage bootstrap (APP_INITIALIZER) and the async profile fetch.
    effect(() => {
      const supported = ['en', 'es', 'pt'];
      const lang = this.profileStore.profile()?.preferences?.language;
      if (lang && supported.includes(lang) && lang !== this.translocoService.getActiveLang()) {
        this.translocoService.setActiveLang(lang);
        localStorage.setItem('app.language', lang);
      }
    });
  }

  ngOnInit(): void {
    // Load user profile eagerly so language preference is available before
    // navigating to Settings. The ProfileStore has a double-load guard.
    this.profileStore.loadProfile();

    // Load notifications and connect WebSocket on app startup
    this.notificationStore.loadNotifications();

    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    const userId = claims?.['sub'];
    const token  = this.oauth.getAccessToken();

    if (userId && token) {
      this.notificationStore.connectWebSocket(userId, token);
    }
  }
}
