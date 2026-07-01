import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  output,
  HostListener,
  OnInit,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ThemeService, Theme } from '../../../core/theme/theme.service';

interface ThemeOption {
  id: Theme;
  label: string;
  description: string;
  tags: string[];
  /** Hardcoded hex values — previews must work regardless of active theme. */
  preview: {
    bg: string;
    surface: string;
    accent: string;
    accentSoft: string;
    text: string;
    textMuted: string;
    sidebar: string;
    border: string;
  };
}

const THEMES: ThemeOption[] = [
  {
    id: 'midnight',
    label: 'Midnight',
    description: 'Deep space editorial',
    tags: ['dark', 'violet'],
    preview: {
      bg:         '#0d0e14',
      surface:    '#161820',
      accent:     '#9b6dff',
      accentSoft: '#9b6dff22',
      text:       '#e8eaf2',
      textMuted:  '#5a5e72',
      sidebar:    '#0b0c12',
      border:     '#2a2d3a',
    },
  },
  {
    id: 'amber',
    label: 'Amber',
    description: 'Warm cream & gold',
    tags: ['light', 'warm'],
    preview: {
      bg:         '#faf6ee',
      surface:    '#fffdf8',
      accent:     '#d97706',
      accentSoft: '#d9770618',
      text:       '#1c1208',
      textMuted:  '#8a7050',
      sidebar:    '#f2ece0',
      border:     '#e8dfc8',
    },
  },
  {
    id: 'catppuccin',
    label: 'Catppuccin',
    description: 'Mocha rosado',
    tags: ['dark', 'pink'],
    preview: {
      bg:         '#1e1e2e',
      surface:    '#252535',
      accent:     '#f38ba8',
      accentSoft: '#f38ba822',
      text:       '#cdd6f4',
      textMuted:  '#6c7086',
      sidebar:    '#181825',
      border:     '#313244',
    },
  },
  {
    id: 'nord',
    label: 'Nord',
    description: 'Polar frost',
    tags: ['dark', 'cyan'],
    preview: {
      bg:         '#2e3440',
      surface:    '#3b4252',
      accent:     '#88c0d0',
      accentSoft: '#88c0d022',
      text:       '#eceff4',
      textMuted:  '#7b8a9a',
      sidebar:    '#272c36',
      border:     '#434c5e',
    },
  },
  {
    id: 'rose',
    label: 'Rose',
    description: 'Light magenta editorial',
    tags: ['light', 'rose'],
    preview: {
      bg:         '#fdf8f9',
      surface:    '#ffffff',
      accent:     '#e0366e',
      accentSoft: '#e0366e18',
      text:       '#1a0a10',
      textMuted:  '#9a6070',
      sidebar:    '#f7eef1',
      border:     '#f0dde4',
    },
  },
];

@Component({
  selector: 'app-theme-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslocoPipe],
  template: `
    <!-- Backdrop -->
    <div
      class="drawer-backdrop"
      [class.drawer-backdrop--visible]="mounted()"
      (click)="dismiss()"
      aria-hidden="true"
    ></div>

    <!-- Drawer panel -->
    <div
      class="drawer"
      [class.drawer--visible]="mounted()"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="'themes.selector' | transloco"
    >
      <!-- Header -->
      <div class="drawer-header">
        <div class="drawer-header-left">
          <span class="material-symbols-rounded drawer-header-icon" aria-hidden="true">palette</span>
          <div>
            <h2 class="drawer-title">{{ 'themes.title' | transloco }}</h2>
            <p class="drawer-subtitle">{{ 'themes.subtitle' | transloco }}</p>
          </div>
        </div>
        <button class="drawer-close" (click)="dismiss()" [attr.aria-label]="'themes.close' | transloco">
          <span class="material-symbols-rounded" aria-hidden="true">close</span>
        </button>
      </div>

      <!-- Theme cards -->
      <div class="theme-grid" role="listbox" [attr.aria-label]="'themes.available' | transloco">

        @for (t of themes; track t.id) {
          <button
            class="theme-card"
            [class.theme-card--active]="confirmed() === t.id"
            [class.theme-card--previewing]="previewing() === t.id && previewing() !== confirmed()"
            role="option"
            [attr.aria-selected]="confirmed() === t.id"
            [attr.aria-label]="t.label + ' — ' + t.description"
            (mouseenter)="onHover(t.id)"
            (mouseleave)="onLeave()"
            (click)="onSelect(t.id)"
            (focus)="onHover(t.id)"
            (blur)="onLeave()"
          >
            <!-- Full-size preview mockup -->
            <div class="preview-shell" [style.background]="t.preview.bg">
              <!-- Fake sidebar -->
              <div class="preview-sidebar" [style.background]="t.preview.sidebar" [style.border-right-color]="t.preview.border">
                <!-- Logo dot -->
                <div class="ps-logo" [style.background]="t.preview.accent"></div>
                <!-- Nav items -->
                <div class="ps-nav">
                  @for (i of [0,1,2]; track i) {
                    <div
                      class="ps-nav-item"
                      [class.ps-nav-item--active]="i === 0"
                      [style.background]="i === 0 ? t.preview.accentSoft : 'transparent'"
                    >
                      <div class="ps-nav-icon" [style.background]="i === 0 ? t.preview.accent : t.preview.textMuted"></div>
                      <div class="ps-nav-label" [style.background]="i === 0 ? t.preview.text + 'cc' : t.preview.textMuted + '88'"></div>
                    </div>
                  }
                </div>
                <!-- Bottom dot -->
                <div class="ps-bottom">
                  <div class="ps-avatar" [style.background]="t.preview.accentSoft" [style.border-color]="t.preview.accent + '44'"></div>
                </div>
              </div>

              <!-- Fake main content -->
              <div class="preview-main">
                <!-- Top bar -->
                <div class="pm-topbar" [style.border-bottom-color]="t.preview.border">
                  <div class="pm-page-title" [style.background]="t.preview.text + 'dd'"></div>
                  <div class="pm-actions">
                    <div class="pm-btn" [style.background]="t.preview.accent"></div>
                  </div>
                </div>
                <!-- Card grid -->
                <div class="pm-cards">
                  @for (c of [0,1,2]; track c) {
                    <div
                      class="pm-card"
                      [style.background]="t.preview.surface"
                      [style.border-color]="t.preview.border"
                    >
                      <div class="pm-card-bar" [style.background]="t.preview.accent + (c === 0 ? '' : '66')"></div>
                      <div class="pm-card-title" [style.background]="t.preview.text + 'cc'"></div>
                      <div class="pm-card-line" [style.background]="t.preview.textMuted + '66'"></div>
                      <div class="pm-card-chip" [style.background]="t.preview.accentSoft" [style.border-color]="t.preview.accent + '44'"></div>
                    </div>
                  }
                </div>
              </div>
            </div>

            <!-- Card footer -->
            <div class="theme-card-footer" [style.background]="t.preview.bg" [style.border-top-color]="t.preview.border">
              <div class="theme-card-info">
                <span class="theme-card-name" [style.color]="t.preview.text">{{ t.label }}</span>
                <span class="theme-card-desc" [style.color]="t.preview.textMuted">{{ t.description }}</span>
              </div>
              <div class="theme-card-tags">
                @for (tag of t.tags; track tag) {
                  <span class="theme-tag" [style.background]="t.preview.accentSoft" [style.color]="t.preview.accent">
                    {{ tag }}
                  </span>
                }
              </div>
            </div>

            <!-- Active indicator -->
            @if (confirmed() === t.id) {
              <div class="theme-check" aria-hidden="true">
                <span class="material-symbols-rounded">check_circle</span>
              </div>
            }

            <!-- Hover preview indicator -->
            @if (previewing() === t.id && previewing() !== confirmed()) {
              <div class="theme-preview-badge" aria-hidden="true">{{ 'themes.preview' | transloco }}</div>
            }
          </button>
        }
      </div>

      <!-- Footer hint -->
      <p class="drawer-hint">
        <span class="material-symbols-rounded" aria-hidden="true" style="font-size:0.875rem; vertical-align: middle;">info</span>
        {{ 'themes.savedHint' | transloco }}
      </p>
    </div>

    <style>
      /* ── Host fills viewport ────────────────────────── */
      :host {
        position: fixed;
        inset: 0;
        z-index: 9999;
        display: flex;
        align-items: center;
        justify-content: center;
        pointer-events: none;
      }
      :host(.active) { pointer-events: all; }

      /* ── Backdrop ───────────────────────────────────── */
      .drawer-backdrop {
        position: fixed;
        inset: 0;
        background: oklch(0 0 0 / 0);
        backdrop-filter: blur(0px);
        transition:
          background 0.25s ease,
          backdrop-filter 0.25s ease;
        pointer-events: none;
      }
      .drawer-backdrop--visible {
        background: oklch(0 0 0 / 0.55);
        backdrop-filter: blur(4px);
        pointer-events: all;
      }

      /* ── Drawer panel ───────────────────────────────── */
      .drawer {
        position: relative;
        z-index: 1;
        width: min(900px, 92vw);
        background: var(--color-bg-elevated);
        border: 1px solid var(--color-border-strong);
        border-radius: 1.25rem;
        box-shadow:
          0 24px 80px oklch(0 0 0 / 0.45),
          0 0 0 1px color-mix(in oklch, var(--color-accent) 10%, transparent);
        overflow: hidden;
        pointer-events: all;
        opacity: 0;
        transform: translateY(16px) scale(0.97);
        transition:
          opacity 0.28s cubic-bezier(0.34, 1.56, 0.64, 1),
          transform 0.28s cubic-bezier(0.34, 1.56, 0.64, 1);
      }
      .drawer--visible {
        opacity: 1;
        transform: translateY(0) scale(1);
      }

      /* ── Header ─────────────────────────────────────── */
      .drawer-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1rem 1.25rem 0.75rem;
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 60%, transparent);
      }
      .drawer-header-left {
        display: flex;
        align-items: center;
        gap: 0.75rem;
      }
      .drawer-header-icon {
        font-size: 1.5rem;
        color: var(--color-accent);
      }
      .drawer-title {
        font-size: 1rem;
        font-weight: 700;
        color: var(--color-text-primary);
        font-family: 'Outfit', sans-serif;
        line-height: 1.2;
        margin: 0;
      }
      .drawer-subtitle {
        font-size: 0.75rem;
        color: var(--color-text-muted);
        margin: 0;
        margin-top: 0.1rem;
      }
      .drawer-close {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 2rem;
        height: 2rem;
        border-radius: 0.5rem;
        border: none;
        background: transparent;
        color: var(--color-text-muted);
        cursor: pointer;
        transition: color 0.15s ease, background 0.15s ease;
        outline-offset: 2px;
      }
      .drawer-close:hover {
        color: var(--color-text-primary);
        background: color-mix(in oklch, var(--color-bg-overlay) 80%, transparent);
      }
      .drawer-close:focus-visible { outline: 2px solid var(--color-accent); }
      .drawer-close .material-symbols-rounded { font-size: 1.125rem; }

      /* ── Theme grid ─────────────────────────────────── */
      .theme-grid {
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        gap: 1rem;
        padding: 1.25rem 1.5rem;
      }

      /* ── Theme card ─────────────────────────────────── */
      .theme-card {
        position: relative;
        display: flex;
        flex-direction: column;
        border-radius: 0.875rem;
        border: 2px solid var(--color-border);
        overflow: hidden;
        cursor: pointer;
        background: transparent;
        padding: 0;
        transition:
          border-color 0.18s ease,
          transform 0.15s ease,
          box-shadow 0.18s ease;
        outline-offset: 3px;
        -webkit-tap-highlight-color: transparent;
      }
      .theme-card:hover,
      .theme-card--previewing {
        border-color: var(--color-border-strong);
        transform: translateY(-2px);
        box-shadow: 0 8px 28px oklch(0 0 0 / 0.25);
      }
      .theme-card:focus-visible { outline: 2px solid var(--color-accent); }
      .theme-card--active {
        border-color: var(--color-accent) !important;
        box-shadow:
          0 0 0 3px color-mix(in oklch, var(--color-accent) 20%, transparent),
          0 8px 28px oklch(0 0 0 / 0.25) !important;
      }

      /* ── Preview shell — fake app UI ────────────────── */
      .preview-shell {
        display: flex;
        flex-direction: row;
        height: 88px;
        overflow: hidden;
        flex-shrink: 0;
      }

      /* Fake sidebar */
      .preview-sidebar {
        width: 26px;
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 6px 0;
        gap: 0;
        border-right: 1px solid;
        flex-shrink: 0;
      }
      .ps-logo {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        margin-bottom: 5px;
        flex-shrink: 0;
      }
      .ps-nav {
        display: flex;
        flex-direction: column;
        gap: 2px;
        width: 100%;
        padding: 0 3px;
        flex: 1;
      }
      .ps-nav-item {
        display: flex;
        align-items: center;
        gap: 2px;
        padding: 2px 3px;
        border-radius: 3px;
      }
      .ps-nav-item--active {}
      .ps-nav-icon {
        width: 6px;
        height: 6px;
        border-radius: 1px;
        flex-shrink: 0;
      }
      .ps-nav-label {
        height: 3px;
        border-radius: 2px;
        flex: 1;
      }
      .ps-bottom {
        padding: 0 3px;
        width: 100%;
      }
      .ps-avatar {
        width: 14px;
        height: 14px;
        border-radius: 50%;
        border: 1px solid;
        margin: 0 auto;
      }

      /* Fake main content */
      .preview-main {
        flex: 1;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
      .pm-topbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 4px 6px;
        border-bottom: 1px solid;
        flex-shrink: 0;
      }
      .pm-page-title {
        height: 4px;
        width: 36px;
        border-radius: 2px;
      }
      .pm-actions { display: flex; gap: 3px; }
      .pm-btn {
        width: 16px;
        height: 7px;
        border-radius: 2px;
      }
      .pm-cards {
        display: flex;
        gap: 4px;
        padding: 5px;
        flex: 1;
        overflow: hidden;
      }
      .pm-card {
        flex: 1;
        border-radius: 4px;
        border: 1px solid;
        padding: 4px;
        display: flex;
        flex-direction: column;
        gap: 2px;
        overflow: hidden;
      }
      .pm-card-bar {
        height: 2px;
        border-radius: 9999px;
        width: 55%;
      }
      .pm-card-title {
        height: 3px;
        border-radius: 2px;
        width: 90%;
        margin-top: 1px;
      }
      .pm-card-line {
        height: 3px;
        border-radius: 2px;
        width: 65%;
      }
      .pm-card-chip {
        height: 6px;
        border-radius: 9999px;
        width: 24px;
        border: 1px solid;
        margin-top: 1px;
      }

      /* ── Card footer ─────────────────────────────────── */
      .theme-card-footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.45rem 0.6rem;
        border-top: 1px solid;
        gap: 0.375rem;
        flex-shrink: 0;
      }
      .theme-card-info {
        display: flex;
        flex-direction: column;
        gap: 0.05rem;
        min-width: 0;
      }
      .theme-card-name {
        font-size: 0.6875rem;
        font-weight: 700;
        font-family: 'Outfit', sans-serif;
        line-height: 1.2;
      }
      .theme-card-desc {
        font-size: 0.5625rem;
        line-height: 1.2;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .theme-card-tags {
        display: flex;
        gap: 0.2rem;
        flex-wrap: nowrap;
        flex-shrink: 0;
      }
      .theme-tag {
        font-size: 0.5625rem;
        font-weight: 600;
        padding: 0.1rem 0.35rem;
        border-radius: 9999px;
        white-space: nowrap;
        letter-spacing: 0.03em;
      }

      /* ── Active check ───────────────────────────────── */
      .theme-check {
        position: absolute;
        top: 0.5rem;
        right: 0.5rem;
        color: var(--color-accent);
        background: var(--color-bg-elevated);
        border-radius: 50%;
        line-height: 1;
        box-shadow: 0 2px 8px oklch(0 0 0 / 0.3);
      }
      .theme-check .material-symbols-rounded { font-size: 1.125rem; display: block; }

      /* ── Preview badge ──────────────────────────────── */
      .theme-preview-badge {
        position: absolute;
        top: 0.5rem;
        left: 0.5rem;
        font-size: 0.5625rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        padding: 0.15rem 0.4rem;
        border-radius: 9999px;
        background: var(--color-accent);
        color: var(--color-bg-base);
        box-shadow: 0 2px 8px oklch(0 0 0 / 0.3);
      }

      /* ── Footer hint ────────────────────────────────── */
      .drawer-hint {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.6875rem;
        color: var(--color-text-muted);
        padding: 0 1.25rem 0.875rem;
        margin: 0;
      }
      .drawer-hint .material-symbols-rounded { flex-shrink: 0; }
    </style>
  `,
})
export class ThemeDrawerComponent implements OnInit {
  readonly close = output<void>();

  readonly themeService = inject(ThemeService);

  readonly themes = THEMES;

  /** Theme being actively hovered/previewed */
  readonly previewing = signal<Theme | null>(null);
  /** Theme that was selected before any hover */
  readonly confirmed  = signal<Theme>(this.themeService.theme());

  /** Animate-in on next frame */
  readonly mounted = signal(false);

  ngOnInit(): void {
    // Trigger CSS transition on next frame
    requestAnimationFrame(() => this.mounted.set(true));
  }

  onHover(id: Theme): void {
    this.previewing.set(id);
    this.themeService.setTheme(id);
  }

  onLeave(): void {
    if (this.previewing() !== null) {
      this.previewing.set(null);
      // Restore the confirmed theme
      this.themeService.setTheme(this.confirmed());
    }
  }

  onSelect(id: Theme): void {
    this.confirmed.set(id);
    this.themeService.setTheme(id);
    // Small delay so the user sees the active ring before close
    setTimeout(() => this.dismiss(), 180);
  }

  dismiss(): void {
    this.mounted.set(false);
    // Wait for exit transition before destroying
    setTimeout(() => this.close.emit(), 250);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.dismiss();
  }
}
