import { inject, Injectable, signal, computed } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export type Theme = 'dark' | 'light';

const STORAGE_KEY = 'epm-theme';
const DEFAULT_THEME: Theme = 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly _theme = signal<Theme>(this._resolveInitialTheme());

  readonly theme    = this._theme.asReadonly();
  readonly isDark   = computed(() => this._theme() === 'dark');
  readonly isLight  = computed(() => this._theme() === 'light');

  constructor() {
    // Apply the initial theme immediately — synchronous, no render cycle needed.
    // Using afterNextRender only if we needed deferred DOM interaction;
    // here we apply eagerly so there's no flash of wrong theme.
    this._applyToDocument(this._theme());
  }

  toggle(): void {
    this.setTheme(this._theme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this._theme.set(theme);
    this._applyToDocument(theme);
    this._persist(theme);
  }

  // ─── Private helpers ────────────────────────────────────────────────────────

  private _resolveInitialTheme(): Theme {
    try {
      const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
      if (stored === 'dark' || stored === 'light') return stored;
    } catch {
      // localStorage unavailable (SSR or blocked)
    }
    return DEFAULT_THEME;
  }

  private _applyToDocument(theme: Theme): void {
    const root = this.document.documentElement;
    root.setAttribute('data-theme', theme);
  }

  private _persist(theme: Theme): void {
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // localStorage unavailable — fail silently
    }
  }
}
