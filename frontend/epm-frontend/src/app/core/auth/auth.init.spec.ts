/**
 * Tests for auth.init.ts — public-route bypass logic.
 *
 * Strategy: extract and test the pure routing decision function
 * `shouldSkipCodeFlow(pathname: string): boolean` directly.
 * This avoids the need to mock OAuthService for the bypass check itself.
 */
import { shouldSkipCodeFlow } from './auth.init';

describe('shouldSkipCodeFlow', () => {
  // ── RED: /register must skip initCodeFlow ──────────────────────────────────

  it('returns true when pathname is /register', () => {
    expect(shouldSkipCodeFlow('/register')).toBe(true);
  });

  // ── TRIANGULATE: /accept-invitation must also skip ─────────────────────────

  it('returns true when pathname is /accept-invitation', () => {
    expect(shouldSkipCodeFlow('/accept-invitation')).toBe(true);
  });

  // ── TRIANGULATE: sub-paths of public routes also skip ─────────────────────

  it('returns true when pathname starts with /register (e.g. /register/confirm)', () => {
    expect(shouldSkipCodeFlow('/register/confirm')).toBe(true);
  });

  it('returns true when pathname starts with /accept-invitation with query-like suffix', () => {
    expect(shouldSkipCodeFlow('/accept-invitation')).toBe(true);
  });

  // ── TRIANGULATE: guarded routes MUST NOT skip ─────────────────────────────

  it('returns false for /projects (guarded route)', () => {
    expect(shouldSkipCodeFlow('/projects')).toBe(false);
  });

  it('returns false for / (root, redirected to projects)', () => {
    expect(shouldSkipCodeFlow('/')).toBe(false);
  });

  it('returns false for /settings (guarded route)', () => {
    expect(shouldSkipCodeFlow('/settings')).toBe(false);
  });

  // ── TRIANGULATE: paths that begin with similar prefixes must NOT skip ──────

  it('returns false for /registered (starts-with guard is correct)', () => {
    expect(shouldSkipCodeFlow('/registered')).toBe(false);
  });
});
