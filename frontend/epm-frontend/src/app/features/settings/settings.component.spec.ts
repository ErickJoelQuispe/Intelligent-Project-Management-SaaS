import { TestBed, ComponentFixture } from '@angular/core/testing';
import { SettingsComponent } from './settings.component';
import { AppPreferencesService } from '../../core/services/app-preferences.service';
import { ThemeService } from '../../core/theme/theme.service';
import { NotificationPreferencesStore } from '../notifications/store/notification-preferences.store';
import { ProfileStore } from './store/profile.store';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from '../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';

/**
 * Angular Material 21 renders mat-slide-toggle with the aria-label and
 * aria-checked on the inner button[role="switch"], not on the host element.
 * Helper returns that button for the named toggle.
 */
function findToggleButton(compiled: HTMLElement, ariaLabel: string): HTMLButtonElement | undefined {
  return Array.from(compiled.querySelectorAll<HTMLButtonElement>('button[role="switch"]'))
    .find(btn => btn.getAttribute('aria-label') === ariaLabel);
}

describe('SettingsComponent — appearance wiring', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let compiled: HTMLElement;
  let prefServiceMock: {
    compactMode: ReturnType<typeof signal<boolean>>;
    reduceAnimations: ReturnType<typeof signal<boolean>>;
    setCompactMode: ReturnType<typeof vi.fn>;
    setReduceAnimations: ReturnType<typeof vi.fn>;
  };

  function setup(initialCompact = false, initialReduceAnim = false) {
    prefServiceMock = {
      compactMode:         signal(initialCompact),
      reduceAnimations:    signal(initialReduceAnim),
      setCompactMode:      vi.fn(),
      setReduceAnimations: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppPreferencesService, useValue: prefServiceMock },
        { provide: ThemeService, useValue: { theme: signal('midnight'), setTheme: vi.fn(), isDark: signal(true) } },
        { provide: NotificationPreferencesStore, useValue: { loading: signal(false), preferences: signal([]), loadPreferences: vi.fn() } },
        { provide: ProfileStore, useValue: { loading: signal(false), saving: signal(false), saveSuccess: signal(false), error: signal(null), profile: signal(null), loadProfile: vi.fn(), saveProfile: vi.fn() } },
        { provide: OAuthService, useValue: { getIdentityClaims: vi.fn().mockReturnValue({}), getAccessTokenExpiration: vi.fn().mockReturnValue(Date.now() + 3600000) } },
        { provide: AuthService, useValue: { logout: vi.fn() } },
      ],
    });

    fixture = TestBed.createComponent(SettingsComponent);
    compiled = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function goToAppearance() {
    const buttons = Array.from(compiled.querySelectorAll('button.settings-nav-item'));
    const btn = buttons.find(b => b.textContent?.includes('Appearance')) as HTMLButtonElement;
    btn?.click();
    fixture.detectChanges();
  }

  function goToSecurity() {
    const buttons = Array.from(compiled.querySelectorAll('button.settings-nav-item'));
    const btn = buttons.find(b => b.textContent?.includes('Security')) as HTMLButtonElement;
    btn?.click();
    fixture.detectChanges();
  }

  // ─── Compact mode toggle — false state ────────────────────────────────────

  it('compact-mode toggle is rendered and aria-checked=false when compactMode()=false', () => {
    setup(false);
    goToAppearance();

    const btn = findToggleButton(compiled, 'Toggle compact mode');

    expect(btn).toBeTruthy();
    expect(btn?.getAttribute('aria-checked')).toBe('false');
  });

  // ─── Compact mode toggle — true state (TRIANGULATE) ───────────────────────

  it('compact-mode toggle has aria-checked=true when compactMode()=true', () => {
    setup(true);  // different input: compactMode starts true
    goToAppearance();

    const btn = findToggleButton(compiled, 'Toggle compact mode');

    expect(btn).toBeTruthy();
    expect(btn?.getAttribute('aria-checked')).toBe('true');
  });

  // ─── Reduce animations toggle — false state ───────────────────────────────

  it('reduce-animations toggle is rendered and aria-checked=false when reduceAnimations()=false', () => {
    setup(false, false);
    goToAppearance();

    const btn = findToggleButton(compiled, 'Toggle reduce animations');

    expect(btn).toBeTruthy();
    expect(btn?.getAttribute('aria-checked')).toBe('false');
  });

  // ─── Reduce animations toggle — true state (TRIANGULATE) ─────────────────

  it('reduce-animations toggle has aria-checked=true when reduceAnimations()=true', () => {
    setup(false, true);  // different input: reduceAnimations starts true
    goToAppearance();

    const btn = findToggleButton(compiled, 'Toggle reduce animations');

    expect(btn).toBeTruthy();
    expect(btn?.getAttribute('aria-checked')).toBe('true');
  });

  // ─── 2FA section removal ──────────────────────────────────────────────────

  it('should NOT render a Two-factor authentication heading in the Security tab', () => {
    setup();
    goToSecurity();

    const headings = Array.from(compiled.querySelectorAll('h3'));
    const twoFaHeading = headings.find(h => h.textContent?.includes('Two-factor authentication'));

    expect(twoFaHeading).toBeUndefined();
  });

  it('should NOT have a 2FA mat-slide-toggle button in the Security tab', () => {
    setup();
    goToSecurity();

    const twoFaToggleBtn = findToggleButton(compiled, 'Toggle two-factor authentication');

    expect(twoFaToggleBtn).toBeUndefined();
  });
});
