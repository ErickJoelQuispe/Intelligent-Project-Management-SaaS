import { Injectable, signal } from '@angular/core';

const COMPACT_MODE_KEY    = 'app.compactMode';
const REDUCE_ANIM_KEY     = 'app.reduceAnimations';
const CSS_COMPACT_VAR     = '--app-compact-mode';
const CSS_REDUCE_ANIM_VAR = '--app-reduce-animations';

@Injectable({ providedIn: 'root' })
export class AppPreferencesService {
  readonly compactMode      = signal<boolean>(this._readBool(COMPACT_MODE_KEY));
  readonly reduceAnimations = signal<boolean>(this._readBool(REDUCE_ANIM_KEY));

  constructor() {
    // Apply initial values to CSS on startup
    this._applyCssVar(CSS_COMPACT_VAR, this.compactMode());
    this._applyCssVar(CSS_REDUCE_ANIM_VAR, this.reduceAnimations());
  }

  setCompactMode(value: boolean): void {
    this.compactMode.set(value);
    localStorage.setItem(COMPACT_MODE_KEY, String(value));
    this._applyCssVar(CSS_COMPACT_VAR, value);
  }

  setReduceAnimations(value: boolean): void {
    this.reduceAnimations.set(value);
    localStorage.setItem(REDUCE_ANIM_KEY, String(value));
    this._applyCssVar(CSS_REDUCE_ANIM_VAR, value);
  }

  // ─── Private helpers ───────────────────────────────────────────────────────

  private _readBool(key: string): boolean {
    try {
      return localStorage.getItem(key) === 'true';
    } catch {
      return false;
    }
  }

  private _applyCssVar(varName: string, value: boolean): void {
    document.documentElement.style.setProperty(varName, value ? '1' : '0');
  }
}
