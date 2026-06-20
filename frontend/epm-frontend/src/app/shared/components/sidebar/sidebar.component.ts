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
      class="sidebar-root relative flex flex-col h-screen shrink-0 overflow-hidden"
      [class.sidebar-collapsed]="collapsed()"
      role="navigation"
      aria-label="Main navigation"
    >

      <!-- Top accent line -->
      <div class="sidebar-top-line" aria-hidden="true"></div>

      <!-- Ambient glow -->
      <div class="sidebar-ambient-glow" aria-hidden="true"></div>

      <!-- ═══ Brand ═══ -->
      <header class="sidebar-brand" [class.sidebar-brand--collapsed]="collapsed()">

        <!-- Logo mark — geometric SVG mark -->
        <div class="logomark" aria-hidden="true">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- Outer hexagon -->
            <path d="M14 2L25.258 8.5V21.5L14 28L2.742 21.5V8.5L14 2Z"
                  fill="url(#logo-grad-outer)" opacity="0.15"/>
            <!-- Inner hexagon -->
            <path d="M14 7L21.794 11.5V20.5L14 25L6.206 20.5V11.5L14 7Z"
                  fill="url(#logo-grad-outer)" opacity="0.25"/>
            <!-- Core shape -->
            <path d="M14 10.5L19.196 13.5V19.5L14 22.5L8.804 19.5V13.5L14 10.5Z"
                  fill="url(#logo-grad-core)"/>
            <!-- Center dot -->
            <circle cx="14" cy="16.5" r="2" fill="white" opacity="0.9"/>
            <defs>
              <linearGradient id="logo-grad-outer" x1="2.742" y1="2" x2="25.258" y2="28" gradientUnits="userSpaceOnUse">
                <stop stop-color="var(--color-accent)"/>
                <stop offset="1" stop-color="var(--color-cyan)"/>
              </linearGradient>
              <linearGradient id="logo-grad-core" x1="8.804" y1="10.5" x2="19.196" y2="22.5" gradientUnits="userSpaceOnUse">
                <stop stop-color="var(--color-accent)"/>
                <stop offset="1" stop-color="var(--color-cyan)"/>
              </linearGradient>
            </defs>
          </svg>
          <div class="logomark-glow" aria-hidden="true"></div>
        </div>

        @if (!collapsed()) {
          <div class="brand-text animate-fade-up">
            <span class="brand-name">EPM</span>
            <span class="brand-sub">Project Management</span>
          </div>
        }
      </header>

      <!-- Divider -->
      <div class="sidebar-divider mx-3" aria-hidden="true"></div>

      <!-- ═══ Navigation ═══ -->
      <nav class="flex-1 overflow-y-auto py-3 px-2 flex flex-col gap-0.5"
           aria-label="Primary navigation">

        @for (item of navItems; track item.route) {
          <a [routerLink]="item.route"
             routerLinkActive="active-nav"
             [routerLinkActiveOptions]="{ exact: false }"
             class="nav-item"
             [class.nav-item--collapsed]="collapsed()"
             [attr.aria-label]="collapsed() ? item.label : null"
             [title]="collapsed() ? item.label : ''"
          >
            <!-- Active pill background -->
            <span class="nav-item-bg" aria-hidden="true"></span>

            <!-- Active accent bar -->
            <span class="nav-item-bar" aria-hidden="true"></span>

            <!-- Icon -->
            <span class="material-symbols-rounded nav-item-icon" aria-hidden="true">{{ item.icon }}</span>

            @if (!collapsed()) {
              <span class="nav-item-label">{{ item.label }}</span>
            }
          </a>
        }
      </nav>

      <!-- ═══ Footer ═══ -->
      <footer class="sidebar-footer">

        <!-- Notifications -->
        <div class="sidebar-footer-row sidebar-footer-row--center">
          <app-notification-bell />
        </div>

        <!-- User card -->
        @if (!collapsed()) {
          <div class="user-card mx-2 mb-1">
            <div class="user-avatar" aria-hidden="true">
              <span class="user-avatar-initial">{{ userInitial() }}</span>
              <span class="user-avatar-ring" aria-hidden="true"></span>
              <span class="user-online-dot" aria-hidden="true"></span>
            </div>
            <span class="user-name">{{ userName() }}</span>
            <button
              (click)="logout()"
              class="user-logout-btn"
              aria-label="Logout"
              title="Logout"
            >
              <span class="material-symbols-rounded" aria-hidden="true">logout</span>
            </button>
          </div>
        }

        <!-- Theme + Collapse row -->
        <div class="sidebar-controls mx-2 mb-1" [class.sidebar-controls--collapsed]="collapsed()">

          <!-- Theme toggle -->
          <button
            (click)="themeService.toggle()"
            class="ctrl-btn"
            [class.ctrl-btn--icon-only]="collapsed()"
            [title]="themeService.isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
            [attr.aria-label]="themeService.isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
          >
            <span class="material-symbols-rounded ctrl-btn-icon" aria-hidden="true">
              {{ themeService.isDark() ? 'light_mode' : 'dark_mode' }}
            </span>
            @if (!collapsed()) {
              <span class="ctrl-btn-label">{{ themeService.isDark() ? 'Light mode' : 'Dark mode' }}</span>
            }
          </button>

          <!-- Collapse toggle -->
          <button
            (click)="toggleCollapse()"
            class="ctrl-btn ctrl-btn--collapse"
            [class.ctrl-btn--icon-only]="collapsed()"
            [title]="collapsed() ? 'Expand sidebar' : 'Collapse sidebar'"
            [attr.aria-label]="collapsed() ? 'Expand sidebar' : 'Collapse sidebar'"
            [attr.aria-expanded]="!collapsed()"
          >
            <span
              class="material-symbols-rounded ctrl-btn-icon collapse-icon"
              [class.collapse-icon--rotated]="!collapsed()"
              aria-hidden="true"
            >
              chevron_right
            </span>
            @if (!collapsed()) {
              <span class="ctrl-btn-label">Collapse</span>
            }
          </button>
        </div>

      </footer>

    </aside>

    <style>
      /* ── Root ──────────────────────────────────────── */
      .sidebar-root {
        width: 15rem;
        background: var(--color-sidebar-bg);
        border-right: 1px solid var(--color-border);
        transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
      }
      .sidebar-root.sidebar-collapsed { width: 3.75rem; }

      /* ── Ambient effects ───────────────────────────── */
      .sidebar-top-line {
        position: absolute;
        inset: 0 0 auto 0;
        height: 1px;
        background: linear-gradient(90deg,
          transparent 0%,
          color-mix(in oklch, var(--color-accent) 60%, transparent) 35%,
          color-mix(in oklch, var(--color-cyan) 50%, transparent) 65%,
          transparent 100%
        );
        z-index: 1;
      }

      .sidebar-ambient-glow {
        position: absolute;
        top: -2rem;
        left: 50%;
        transform: translateX(-50%);
        width: 8rem;
        height: 5rem;
        background: var(--color-accent-subtle);
        filter: blur(24px);
        border-radius: 50%;
        pointer-events: none;
        z-index: 0;
      }

      /* ── Brand ─────────────────────────────────────── */
      .sidebar-brand {
        position: relative;
        z-index: 1;
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0 1rem;
        height: 4rem;
        flex-shrink: 0;
      }
      .sidebar-brand--collapsed { justify-content: center; padding: 0; }

      /* ── Logo mark ─────────────────────────────────── */
      .logomark {
        position: relative;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .logomark-glow {
        position: absolute;
        inset: -4px;
        background: radial-gradient(circle, var(--color-accent-glow) 0%, transparent 70%);
        border-radius: 50%;
        opacity: 0.6;
        animation: logo-breathe 3s ease-in-out infinite;
        pointer-events: none;
      }

      @keyframes logo-breathe {
        0%, 100% { opacity: 0.4; transform: scale(1); }
        50%       { opacity: 0.7; transform: scale(1.12); }
      }

      /* ── Brand text ────────────────────────────────── */
      .brand-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
        gap: 0.1rem;
      }
      .brand-name {
        font-family: 'Outfit', sans-serif;
        font-size: 0.9375rem;
        font-weight: 700;
        letter-spacing: 0.06em;
        color: var(--color-text-primary);
        line-height: 1;
      }
      .brand-sub {
        font-size: 0.625rem;
        font-weight: 500;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--color-text-muted);
        line-height: 1;
      }

      /* ── Divider ───────────────────────────────────── */
      .sidebar-divider {
        height: 1px;
        flex-shrink: 0;
        background: linear-gradient(90deg,
          transparent,
          color-mix(in oklch, var(--color-border-strong) 80%, transparent),
          transparent
        );
      }

      /* ── Nav items ─────────────────────────────────── */
      .nav-item {
        position: relative;
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.625rem 0.75rem;
        border-radius: 0.625rem;
        font-size: 0.875rem;
        font-weight: 500;
        color: var(--color-sidebar-text);
        text-decoration: none;
        cursor: pointer;
        transition:
          color 0.15s ease,
          transform 0.1s ease;
        outline-offset: 2px;
        -webkit-tap-highlight-color: transparent;
      }
      .nav-item--collapsed {
        justify-content: center;
        padding: 0.625rem;
      }
      .nav-item:hover {
        color: var(--color-text-primary);
        transform: translateX(1px);
      }
      .nav-item:focus-visible {
        outline: 2px solid var(--color-accent);
      }

      /* Background pill */
      .nav-item-bg {
        position: absolute;
        inset: 0;
        border-radius: 0.625rem;
        background: transparent;
        transition: background 0.15s ease;
        z-index: -1;
      }
      .nav-item:hover .nav-item-bg {
        background: color-mix(in oklch, var(--color-bg-overlay) 80%, transparent);
      }

      /* Left accent bar */
      .nav-item-bar {
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 2.5px;
        height: 0;
        border-radius: 0 2px 2px 0;
        background: linear-gradient(180deg, var(--color-accent), var(--color-cyan));
        transition: height 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      }

      /* Icon */
      .nav-item-icon {
        font-size: 1.25rem;
        flex-shrink: 0;
        color: inherit;
        transition: color 0.15s ease;
      }

      /* Label */
      .nav-item-label {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        letter-spacing: 0.01em;
      }

      /* Active state */
      .nav-item.active-nav {
        color: var(--color-text-primary);
        font-weight: 600;
      }
      .nav-item.active-nav .nav-item-bg {
        background: color-mix(in oklch, var(--color-accent) 9%, var(--color-sidebar-active));
        border: 1px solid color-mix(in oklch, var(--color-accent) 18%, transparent);
      }
      .nav-item.active-nav .nav-item-bar {
        height: 1.375rem;
      }
      .nav-item.active-nav .nav-item-icon {
        color: var(--color-accent);
      }

      /* ── Footer ────────────────────────────────────── */
      .sidebar-footer {
        flex-shrink: 0;
        border-top: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        padding-top: 0.5rem;
      }

      .sidebar-footer-row {
        display: flex;
        padding: 0 0.5rem 0.25rem;
      }
      .sidebar-footer-row--center {
        justify-content: center;
      }

      /* ── User card ─────────────────────────────────── */
      .user-card {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        border-radius: 0.625rem;
        background: color-mix(in oklch, var(--color-bg-surface) 60%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 40%, transparent);
        transition: border-color 0.15s ease;
      }
      .user-card:hover {
        border-color: color-mix(in oklch, var(--color-border-strong) 60%, transparent);
      }

      /* Avatar */
      .user-avatar {
        position: relative;
        flex-shrink: 0;
        width: 1.875rem;
        height: 1.875rem;
        border-radius: 50%;
        background: linear-gradient(
          135deg,
          color-mix(in oklch, var(--color-accent) 25%, var(--color-bg-elevated)),
          color-mix(in oklch, var(--color-cyan) 20%, var(--color-bg-elevated))
        );
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .user-avatar-initial {
        font-size: 0.6875rem;
        font-weight: 700;
        color: var(--color-cyan);
        line-height: 1;
        font-family: 'Outfit', sans-serif;
      }
      .user-avatar-ring {
        position: absolute;
        inset: -2px;
        border-radius: 50%;
        background: conic-gradient(
          from 0deg,
          var(--color-accent),
          var(--color-cyan),
          var(--color-accent)
        );
        z-index: -1;
        opacity: 0.5;
      }
      .user-online-dot {
        position: absolute;
        bottom: 0;
        right: 0;
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: var(--color-success);
        border: 1.5px solid var(--color-sidebar-bg);
        box-shadow: 0 0 6px color-mix(in oklch, var(--color-success) 60%, transparent);
        animation: glow-pulse 2.5s ease-in-out infinite;
      }

      .user-name {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-text-secondary);
      }
      .user-logout-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        width: 1.625rem;
        height: 1.625rem;
        border-radius: 0.375rem;
        color: var(--color-text-muted);
        cursor: pointer;
        transition: color 0.15s ease, background 0.15s ease;
        background: transparent;
        border: none;
        outline-offset: 2px;
      }
      .user-logout-btn:hover {
        color: var(--color-danger);
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
      }
      .user-logout-btn:focus-visible {
        outline: 2px solid var(--color-accent);
      }
      .user-logout-btn .material-symbols-rounded {
        font-size: 1rem;
      }

      /* ── Controls row ──────────────────────────────── */
      .sidebar-controls {
        display: flex;
        gap: 0.25rem;
      }
      .sidebar-controls--collapsed {
        flex-direction: column;
        align-items: center;
        gap: 0.25rem;
      }

      .ctrl-btn {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        border-radius: 0.5rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-text-muted);
        cursor: pointer;
        transition: color 0.15s ease, background 0.15s ease;
        background: transparent;
        border: none;
        flex: 1;
        min-width: 0;
        outline-offset: 2px;
      }
      .ctrl-btn--icon-only {
        flex: none;
        width: 2.375rem;
        height: 2.375rem;
        justify-content: center;
        padding: 0;
      }
      .ctrl-btn:hover {
        color: var(--color-text-primary);
        background: color-mix(in oklch, var(--color-bg-overlay) 70%, transparent);
      }
      .ctrl-btn:focus-visible {
        outline: 2px solid var(--color-accent);
      }

      .ctrl-btn-icon {
        font-size: 1.125rem;
        flex-shrink: 0;
      }
      .ctrl-btn-label {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        text-align: left;
      }

      /* Collapse icon rotation */
      .collapse-icon {
        transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
      }
      .collapse-icon--rotated {
        transform: rotate(180deg);
      }

      /* Collapse button specifics */
      .ctrl-btn--collapse { flex: none; width: auto; }
      .sidebar-collapsed .ctrl-btn--collapse { width: 2.375rem; }
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
