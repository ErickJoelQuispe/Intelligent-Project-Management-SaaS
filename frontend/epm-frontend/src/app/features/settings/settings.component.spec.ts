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
import { UserProfile, UserPreferences } from '../../core/models/user-profile.model';

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

// ── Workspace preferences wiring ──────────────────────────────────────────────

describe('SettingsComponent — workspace preferences wiring', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let compiled: HTMLElement;
  let profileStoreMock: {
    loading: ReturnType<typeof signal<boolean>>;
    saving: ReturnType<typeof signal<boolean>>;
    saveSuccess: ReturnType<typeof signal<boolean>>;
    error: ReturnType<typeof signal<string | null>>;
    profile: ReturnType<typeof signal<UserProfile | null>>;
    loadProfile: ReturnType<typeof vi.fn>;
    saveProfile: ReturnType<typeof vi.fn>;
  };

  function setup(profile: UserProfile | null = null) {
    profileStoreMock = {
      loading:     signal(false),
      saving:      signal(false),
      saveSuccess: signal(false),
      error:       signal(null),
      profile:     signal(profile),
      loadProfile: vi.fn(),
      saveProfile: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppPreferencesService,
          useValue: {
            compactMode: signal(false), reduceAnimations: signal(false),
            setCompactMode: vi.fn(), setReduceAnimations: vi.fn(),
          }
        },
        { provide: ThemeService, useValue: { theme: signal('midnight'), setTheme: vi.fn(), isDark: signal(true) } },
        { provide: NotificationPreferencesStore,
          useValue: { loading: signal(false), preferences: signal([]), loadPreferences: vi.fn() }
        },
        { provide: ProfileStore, useValue: profileStoreMock },
        { provide: OAuthService,
          useValue: { getIdentityClaims: vi.fn().mockReturnValue({}), getAccessTokenExpiration: vi.fn().mockReturnValue(Date.now() + 3600000) }
        },
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

  function goToWorkspace() {
    const buttons = Array.from(compiled.querySelectorAll('button.settings-nav-item'));
    const btn = buttons.find(b => b.textContent?.includes('Workspace')) as HTMLButtonElement;
    btn?.click();
    fixture.detectChanges();
  }

  // ── Save button calls saveProfile with preferences object ─────────────────

  it('workspace Save button calls profileStore.saveProfile with preferences object', () => {
    const profile: UserProfile = {
      id: '11111111-1111-1111-1111-111111111111',
      tenantId: '22222222-2222-2222-2222-222222222222',
      email: 'alice@example.com',
      firstName: 'Alice',
      lastName: 'Smith',
      bio: null,
      avatarUrl: null,
      version: 2,
      preferences: { language: 'en', timezone: 'UTC', dateFormat: 'ISO', startOfWeek: 'MONDAY' },
    };
    setup(profile);
    goToWorkspace();

    const saveBtn = Array.from(compiled.querySelectorAll('app-button'))
      .find(el => el.textContent?.includes('Save workspace preferences'));
    expect(saveBtn).toBeTruthy();

    (saveBtn as HTMLElement).click();
    fixture.detectChanges();

    expect(profileStoreMock.saveProfile).toHaveBeenCalledOnce();
    const callArg = profileStoreMock.saveProfile.mock.calls[0][0];
    expect(callArg.preferences).toMatchObject({
      language: 'en',
      timezone: 'UTC',
      dateFormat: 'ISO',
      startOfWeek: 'MONDAY',
    });
  });

  // ── TRIANGULATE: preferences from profile populate signals on load ─────────

  it('workspace signals are populated from profile preferences when profile loads', () => {
    const profile: UserProfile = {
      id: '11111111-1111-1111-1111-111111111111',
      tenantId: '22222222-2222-2222-2222-222222222222',
      email: 'bob@example.com',
      firstName: 'Bob',
      lastName: 'Jones',
      bio: null,
      avatarUrl: null,
      version: 1,
      preferences: { language: 'es', timezone: 'America/New_York', dateFormat: 'DD/MM/YYYY', startOfWeek: 'SUNDAY' },
    };
    setup(profile);
    goToWorkspace();

    // The SUNDAY start-of-week pill should be active
    const sundayPill = Array.from(compiled.querySelectorAll('button.week-pill'))
      .find(b => b.textContent?.trim() === 'Sunday') as HTMLButtonElement;
    expect(sundayPill).toBeTruthy();
    expect(sundayPill.classList.contains('week-pill--active')).toBe(true);
  });
});
