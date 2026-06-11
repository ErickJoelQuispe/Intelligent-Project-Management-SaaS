import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { App } from './app';
import { NotificationStore } from './features/notifications/store/notification.store';
import { NotificationService } from './features/notifications/services/notification.service';

describe('App', () => {
  beforeEach(async () => {
    const oauthServiceMock = {
      logOut: vi.fn(),
      hasValidAccessToken: vi.fn().mockReturnValue(true),
      getAccessToken: vi.fn().mockReturnValue('mock-token'),
      getIdentityClaims: vi.fn().mockReturnValue({ sub: 'user-test' }),
    };

    const storeMock = {
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

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideAnimations(),
        { provide: OAuthService, useValue: oauthServiceMock },
        { provide: NotificationStore, useValue: storeMock },
        { provide: NotificationService, useValue: {} },
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
});
