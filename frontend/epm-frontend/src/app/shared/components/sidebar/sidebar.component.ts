import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { NotificationBellComponent } from '../../../features/notifications/components/notification-bell/notification-bell.component';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, NotificationBellComponent],
  template: `
    <aside
      class="relative flex flex-col h-screen border-r border-border
             transition-all duration-300 ease-[cubic-bezier(0.2,0,0,1)] shrink-0 overflow-hidden"
      [style.width]="collapsed() ? '3.5rem' : '15rem'"
      [style.background]="'var(--color-sidebar-bg)'"
    >

      <!-- Glow accent at the top of the sidebar -->
      <div class="absolute top-0 left-0 right-0 h-px"
           style="background: linear-gradient(90deg, transparent, var(--color-accent-glow), var(--color-cyan), transparent);">
      </div>

      <!-- Diffuse glow top -->
      <div class="absolute -top-10 left-1/2 -translate-x-1/2 w-32 h-20 rounded-full pointer-events-none"
           style="background: var(--color-accent-subtle); filter: blur(20px);">
      </div>

      <!-- ═══ Logo ═══ -->
      <div class="relative flex items-center gap-3 px-4 h-16 shrink-0">
        <!-- Logo mark with gradient -->
        <div class="relative size-8 rounded-lg shrink-0 flex items-center justify-center"
             style="background: linear-gradient(135deg, var(--color-accent), var(--color-cyan));
                    box-shadow: 0 0 16px var(--color-accent-glow);">
          <span class="text-white font-bold text-sm" style="font-family: 'Outfit', sans-serif;">E</span>
        </div>
        @if (!collapsed()) {
          <div class="flex flex-col min-w-0 animate-fade-up">
            <span class="text-sm font-semibold tracking-wide truncate"
                  style="font-family: 'Outfit', sans-serif; color: var(--color-text-primary);">
              EPM
            </span>
            <span class="text-xs" style="color: var(--color-text-muted); font-size: 0.65rem;">
              Project Management
            </span>
          </div>
        }
      </div>

      <!-- Divider with gradient -->
      <div class="mx-3 h-px shrink-0"
           style="background: linear-gradient(90deg, transparent, var(--color-border), transparent);">
      </div>

      <!-- ═══ Navigation ═══ -->
      <nav class="flex-1 overflow-y-auto py-4 px-2 flex flex-col gap-1">

        @if (!collapsed()) {
          <span class="px-3 mb-1 text-xs font-semibold uppercase tracking-widest"
                style="color: var(--color-text-disabled); font-size: 0.6rem;">
            Navigation
          </span>
        }

        @for (item of navItems; track item.route) {
          <a [routerLink]="item.route"
             routerLinkActive="active-nav"
             [routerLinkActiveOptions]="{ exact: false }"
             class="group relative flex items-center gap-3 px-3 py-2.5 rounded-lg
                    text-sm font-medium no-underline cursor-pointer
                    transition-all duration-150"
             [style.color]="'var(--color-sidebar-text)'"
             [title]="collapsed() ? item.label : ''"
          >
            <!-- Active indicator line -->
            <span class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full opacity-0
                         transition-all duration-200 active-line"
                  style="background: linear-gradient(180deg, var(--color-accent), var(--color-cyan));">
            </span>

            <span class="material-symbols-rounded text-xl shrink-0 transition-colors duration-150
                         group-hover:text-text-primary icon-nav">
              {{ item.icon }}
            </span>

            @if (!collapsed()) {
              <span class="truncate">{{ item.label }}</span>
            }
          </a>
        }
      </nav>

      <!-- ═══ Footer ═══ -->
      <div class="shrink-0 border-t"
           style="border-color: color-mix(in oklch, var(--color-border) 60%, transparent);">

        <!-- Notifications -->
        <div class="flex items-center justify-center py-2">
          <app-notification-bell />
        </div>

        <!-- User info -->
        @if (!collapsed()) {
          <div class="flex items-center gap-2.5 px-3 py-2.5 mx-2 mb-1 rounded-lg
                      transition-colors duration-150 cursor-default"
               style="background: color-mix(in oklch, var(--color-bg-surface) 50%, transparent);">
            <!-- Avatar with gradient -->
            <div class="size-7 rounded-full shrink-0 flex items-center justify-center"
                 style="background: linear-gradient(135deg, var(--color-accent-subtle), color-mix(in oklch, var(--color-cyan) 30%, transparent));
                        border: 1px solid var(--color-accent-subtle);">
              <span class="text-xs font-bold"
                    style="color: var(--color-cyan);">
                {{ userInitial() }}
              </span>
            </div>
            <span class="text-xs truncate flex-1"
                  style="color: var(--color-text-secondary);">
              {{ userName() }}
            </span>
            <button (click)="logout()" title="Logout"
                    class="transition-colors duration-150 cursor-pointer rounded p-0.5
                           hover:text-danger"
                    style="color: var(--color-text-muted);">
              <span class="material-symbols-rounded text-base">logout</span>
            </button>
          </div>
        }

        <!-- Theme toggle -->
        <button (click)="themeService.toggle()"
                class="flex items-center gap-3 px-3 py-2.5 mx-2 mb-1 rounded-lg w-[calc(100%-1rem)]
                       transition-colors duration-150 cursor-pointer"
                style="color: var(--color-text-muted);"
                [title]="themeService.isDark() ? 'Switch to light mode' : 'Switch to dark mode'">
          <span class="material-symbols-rounded text-xl shrink-0">
            {{ themeService.isDark() ? 'light_mode' : 'dark_mode' }}
          </span>
          @if (!collapsed()) {
            <span class="text-sm truncate">
              {{ themeService.isDark() ? 'Light mode' : 'Dark mode' }}
            </span>
          }
        </button>

        <!-- Collapse toggle -->
        <button (click)="toggleCollapse()"
                class="flex items-center justify-center w-full h-9 mb-1
                       transition-colors duration-150 cursor-pointer rounded-lg mx-0"
                style="color: var(--color-text-muted);"
                [title]="collapsed() ? 'Expand' : 'Collapse'">
          <span class="material-symbols-rounded text-base transition-transform duration-300"
                [style.transform]="collapsed() ? 'rotate(0deg)' : 'rotate(180deg)'">
            chevron_right
          </span>
        </button>
      </div>

    </aside>

    <!-- Scoped styles for active nav links -->
    <style>
      .active-nav {
        background: var(--color-sidebar-active) !important;
        color: var(--color-text-primary) !important;
      }
      .active-nav .active-line { opacity: 1 !important; }
      .active-nav .icon-nav    { color: var(--color-accent) !important; }

      a:not(.active-nav):hover {
        background: var(--color-bg-overlay);
        color: var(--color-text-primary) !important;
      }
    </style>
  `,
})
export class SidebarComponent {
  readonly themeService = inject(ThemeService);

  private readonly authService = inject(AuthService);
  private readonly oauth       = inject(OAuthService);
  private readonly router      = inject(Router);

  collapsed = signal(false);

  readonly navItems: NavItem[] = [
    { label: 'Projects', icon: 'folder',   route: '/projects' },
    { label: 'Teams',    icon: 'group',    route: '/teams' },
    { label: 'Settings', icon: 'settings', route: '/settings' },
  ];

  userName = computed(() => {
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    return claims?.['preferred_username'] ?? claims?.['email'] ?? 'User';
  });

  userInitial = computed(() => this.userName().charAt(0).toUpperCase());

  toggleCollapse(): void { this.collapsed.update(v => !v); }
  logout(): void         { this.authService.logout(); }
}
