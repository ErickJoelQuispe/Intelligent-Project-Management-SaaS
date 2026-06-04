import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { OAuthService } from 'angular-oauth2-oidc';
import { NavbarComponent } from './navbar.component';
import { NotificationStore } from '../../../features/notifications/store/notification.store';
import { NotificationService } from '../../../features/notifications/services/notification.service';

function createStoreMock() {
  return {
    notifications: signal([]),
    unreadCount: signal(0),
    loading: signal(false),
    error: signal(null),
    loadNotifications: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    pollNotifications: vi.fn(),
  };
}

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;

  beforeEach(async () => {
    const oauthMock = { logOut: vi.fn() };
    const storeMock = createStoreMock();

    await TestBed.configureTestingModule({
      imports: [NavbarComponent, RouterModule.forRoot([])],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimations(),
        { provide: OAuthService, useValue: oauthMock },
        { provide: NotificationStore, useValue: storeMock },
        { provide: NotificationService, useValue: {} },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
  });

  it('should create the navbar', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the notification bell component', async () => {
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-notification-bell')).toBeTruthy();
  });

  it('renders the logout button', async () => {
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const btn = el.querySelector('[aria-label="Logout"]');
    expect(btn).toBeTruthy();
  });
});
