import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { App } from './app';
import { NotificationStore } from './features/notifications/store/notification.store';
import { NotificationService } from './features/notifications/services/notification.service';
import { ProfileStore } from './features/settings/store/profile.store';
import { TranslocoService } from '@jsverse/transloco';

describe('App', () => {
  let loadProfileSpy: ReturnType<typeof vi.fn>;
  let setActiveLangSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    loadProfileSpy = vi.fn();
    setActiveLangSpy = vi.fn();

    const oauthServiceMock = {
      logOut: vi.fn(),
      hasValidAccessToken: vi.fn().mockReturnValue(true),
      getAccessToken: vi.fn().mockReturnValue('mock-token'),
      getIdentityClaims: vi.fn().mockReturnValue({ sub: 'user-test' }),
    };

    const notifStoreMock = {
      notifications: signal([]),
      unreadCount: signal(0),
      loading: signal(false),
      error: signal(null),
      wsConnected: signal(false),
      loadNotifications: vi.fn(),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
      connectWebSocket: vi.fn(),
    };

    const profileStoreMock = {
      profile: signal(null),
      loading: signal(false),
      saving: signal(false),
      error: signal(null),
      saveSuccess: signal(false),
      loaded: signal(false),
      loadProfile: loadProfileSpy,
      saveProfile: vi.fn(),
    };

    const translocoServiceMock = {
      getActiveLang: vi.fn().mockReturnValue('en'),
      setActiveLang: setActiveLangSpy,
    };

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideAnimations(),
        { provide: OAuthService, useValue: oauthServiceMock },
        { provide: NotificationStore, useValue: notifStoreMock },
        { provide: NotificationService, useValue: {} },
        { provide: ProfileStore, useValue: profileStoreMock },
        { provide: TranslocoService, useValue: translocoServiceMock },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render sidebar', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-sidebar')).toBeTruthy();
  });

  it('should call profileStore.loadProfile() on init', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(loadProfileSpy).toHaveBeenCalledOnce();
  });
});
