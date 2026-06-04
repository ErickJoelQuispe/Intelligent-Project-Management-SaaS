import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationStore } from '../../store/notification.store';
import { NotificationService } from '../../services/notification.service';

function createStoreMock(unreadCount = 0) {
  return {
    unreadCount: signal(unreadCount),
    notifications: signal([]),
    loading: signal(false),
    error: signal(null),
    loadNotifications: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    pollNotifications: vi.fn(),
  };
}

describe('NotificationBellComponent', () => {
  function setup(unreadCount: number): ComponentFixture<NotificationBellComponent> {
    const storeMock = createStoreMock(unreadCount);

    TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimations(),
        { provide: NotificationStore, useValue: storeMock },
        { provide: NotificationService, useValue: {} },
      ],
    });

    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('should create the component', () => {
    const fixture = setup(3);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows badge with unreadCount when count is > 0', async () => {
    const fixture = setup(3);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const badge = compiled.querySelector('[data-testid="bell-badge"]');
    expect(badge).toBeTruthy();
    expect(badge!.textContent!.trim()).toBe('3');
  });

  it('hides badge when unreadCount is 0', async () => {
    const fixture = setup(0);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const badge = compiled.querySelector('[data-testid="bell-badge"]');
    expect(badge).toBeFalsy();
  });

  it('toggles panelOpen on bell button click', async () => {
    const fixture = setup(3);
    await fixture.whenStable();
    const component = fixture.componentInstance;
    expect(component.panelOpen()).toBe(false);
    const btn = fixture.nativeElement.querySelector('[data-testid="bell-button"]') as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();
    expect(component.panelOpen()).toBe(true);
    btn.click();
    fixture.detectChanges();
    expect(component.panelOpen()).toBe(false);
  });
});
