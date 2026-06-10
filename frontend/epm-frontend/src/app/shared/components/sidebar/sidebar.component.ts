import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
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
      style="background: oklch(0.065 0.025 272);"
    >

      <!-- Glow accent en la parte superior del sidebar -->
      <div class="absolute top-0 left-0 right-0 h-px"
           style="background: linear-gradient(90deg, transparent, oklch(0.65 0.26 285 / 0.6), oklch(0.78 0.18 200 / 0.4), transparent);">
      </div>

      <!-- Glow difuso arriba -->
      <div class="absolute -top-10 left-1/2 -translate-x-1/2 w-32 h-20 rounded-full pointer-events-none"
           style="background: oklch(0.65 0.26 285 / 0.15); filter: blur(20px);">
      </div>

      <!-- ═══ Logo ═══ -->
      <div class="relative flex items-center gap-3 px-4 h-16 shrink-0">
        <!-- Logo mark con gradiente -->
        <div class="relative size-8 rounded-lg shrink-0 flex items-center justify-center"
             style="background: linear-gradient(135deg, oklch(0.65 0.26 285), oklch(0.78 0.18 200));
                    box-shadow: 0 0 16px oklch(0.65 0.26 285 / 0.5);">
          <span class="text-white font-bold text-sm" style="font-family: 'Outfit', sans-serif;">E</span>
        </div>
        @if (!collapsed()) {
          <div class="flex flex-col min-w-0 animate-fade-up">
            <span class="text-sm font-semibold tracking-wide truncate"
                  style="font-family: 'Outfit', sans-serif; color: oklch(0.96 0.006 268);">
              EPM
            </span>
            <span class="text-xs" style="color: oklch(0.42 0.012 268); font-size: 0.65rem;">
              Project Management
            </span>
          </div>
        }
      </div>

      <!-- Divider con gradiente -->
      <div class="mx-3 h-px shrink-0"
           style="background: linear-gradient(90deg, transparent, oklch(0.22 0.020 268), transparent);">
      </div>

      <!-- ═══ Navigation ═══ -->
      <nav class="flex-1 overflow-y-auto py-4 px-2 flex flex-col gap-1">

        @if (!collapsed()) {
          <span class="px-3 mb-1 text-xs font-semibold uppercase tracking-widest"
                style="color: oklch(0.28 0.008 268); font-size: 0.6rem;">
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
             style="color: oklch(0.72 0.015 268);"
             [title]="collapsed() ? item.label : ''"
          >
            <!-- Active indicator line -->
            <span class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full opacity-0
                         transition-all duration-200 active-line"
                  style="background: linear-gradient(180deg, oklch(0.65 0.26 285), oklch(0.78 0.18 200));">
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
           style="border-color: oklch(0.22 0.020 268 / 0.6);">

        <!-- Notifications -->
        <div class="flex items-center justify-center py-2">
          <app-notification-bell />
        </div>

        <!-- User info -->
        @if (!collapsed()) {
          <div class="flex items-center gap-2.5 px-3 py-2.5 mx-2 mb-1 rounded-lg
                      transition-colors duration-150 cursor-default"
               style="background: oklch(0.11 0.025 268 / 0.5);">
            <!-- Avatar con gradiente -->
            <div class="size-7 rounded-full shrink-0 flex items-center justify-center"
                 style="background: linear-gradient(135deg, oklch(0.65 0.26 285 / 0.3), oklch(0.78 0.18 200 / 0.3));
                        border: 1px solid oklch(0.65 0.26 285 / 0.3);">
              <span class="text-xs font-bold"
                    style="color: oklch(0.78 0.18 200);">
                {{ userInitial() }}
              </span>
            </div>
            <span class="text-xs truncate flex-1"
                  style="color: oklch(0.65 0.018 268);">
              {{ userName() }}
            </span>
            <button (click)="logout()" title="Logout"
                    class="transition-colors duration-150 cursor-pointer rounded p-0.5
                           hover:text-danger"
                    style="color: oklch(0.42 0.012 268);">
              <span class="material-symbols-rounded text-base">logout</span>
            </button>
          </div>
        }

        <!-- Collapse toggle -->
        <button (click)="toggleCollapse()"
                class="flex items-center justify-center w-full h-9 mb-1
                       transition-colors duration-150 cursor-pointer rounded-lg mx-0"
                style="color: oklch(0.42 0.012 268);"
                [title]="collapsed() ? 'Expand' : 'Collapse'">
          <span class="material-symbols-rounded text-base transition-transform duration-300"
                [style.transform]="collapsed() ? 'rotate(0deg)' : 'rotate(180deg)'">
            chevron_right
          </span>
        </button>
      </div>

    </aside>

    <!-- Estilos scoped para los nav links activos -->
    <style>
      .active-nav {
        background: oklch(0.65 0.26 285 / 0.12) !important;
        color: oklch(0.88 0.015 268) !important;
      }
      .active-nav .active-line { opacity: 1 !important; }
      .active-nav .icon-nav    { color: oklch(0.65 0.26 285) !important; }

      a:not(.active-nav):hover {
        background: oklch(0.20 0.022 268 / 0.6);
        color: oklch(0.88 0.015 268) !important;
      }
    </style>
  `,
})
export class SidebarComponent {
  private readonly oauth  = inject(OAuthService);
  private readonly router = inject(Router);

  collapsed = signal(false);

  readonly navItems: NavItem[] = [
    { label: 'Projects', icon: 'folder',   route: '/projects' },
    { label: 'Settings', icon: 'settings', route: '/settings' },
  ];

  userName = computed(() => {
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    return claims?.['preferred_username'] ?? claims?.['email'] ?? 'User';
  });

  userInitial = computed(() => this.userName().charAt(0).toUpperCase());

  toggleCollapse(): void { this.collapsed.update(v => !v); }
  logout(): void         { this.oauth.logOut(); }
}
