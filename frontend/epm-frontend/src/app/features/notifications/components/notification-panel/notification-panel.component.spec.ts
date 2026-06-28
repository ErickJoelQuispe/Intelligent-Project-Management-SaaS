import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { NotificationPanelComponent } from './notification-panel.component';
import { NotificationStore } from '../../store/notification.store';
import { NotificationService } from '../../services/notification.service';
import { Notification } from '../../models/notification.model';

function mockNotif(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'n1',
    type: 'TASK_CREATED',
    referenceId: 't1',
    message: 'Task created',
    read: false,
    createdAt: '2026-06-04T12:00:00Z',
    ...overrides,
  };
}

function createStoreMock(notifications: Notification[] = []) {
  return {
    notifications: signal(notifications),
    unreadCount: signal(notifications.filter((n) => !n.read).length),
    loading: signal(false),
    error: signal(null),
    loadNotifications: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
  };
}

describe('NotificationPanelComponent', () => {
  function setup(notifications: Notification[]): {
    fixture: ComponentFixture<NotificationPanelComponent>;
    storeMock: ReturnType<typeof createStoreMock>;
  } {
    const storeMock = createStoreMock(notifications);

    TestBed.configureTestingModule({
      imports: [NotificationPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimations(),
        { provide: NotificationStore, useValue: storeMock },
        { provide: NotificationService, useValue: {} },
      ],
    });

    const fixture = TestBed.createComponent(NotificationPanelComponent);
    fixture.detectChanges();
    return { fixture, storeMock };
  }

  afterEach(() => TestBed.resetTestingModule());

  it('shows empty state when no notifications', async () => {
    const { fixture } = setup([]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    // The component uses an inline .notif-empty div (not app-empty-state component)
    const emptyState = el.querySelector('.notif-empty');
    expect(emptyState).toBeTruthy();
    expect(emptyState!.textContent).toContain('caught up');
  });

  it('renders notification items when notifications exist', async () => {
    const notifs = [mockNotif({ id: 'n1' }), mockNotif({ id: 'n2', read: true, message: 'Another task' })];
    const { fixture } = setup(notifs);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).toBeFalsy();
    // notification items are rendered as app-notification-item elements
    const items = el.querySelectorAll('app-notification-item');
    expect(items.length).toBe(2);
  });

  it('shows "Mark read" button only for unread notifications', async () => {
    const notifs = [
      mockNotif({ id: 'n1', read: false }),
      mockNotif({ id: 'n2', read: true }),
    ];
    const { fixture } = setup(notifs);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const items = el.querySelectorAll('app-notification-item');
    // Unread item (n1) is rendered with role="button" on its inner div (clickable to mark as read).
    // Read item (n2) has no role="button" — it is not interactive.
    const unreadInner = items[0].querySelector('[role="button"]');
    const readInner   = items[1].querySelector('[role="button"]');
    expect(unreadInner).toBeTruthy();
    expect(readInner).toBeNull();
  });

  it('calls store.markAsRead when "Mark read" button is clicked', async () => {
    const notifs = [mockNotif({ id: 'n1', read: false })];
    const { fixture, storeMock } = setup(notifs);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    // The unread notification item renders a div with role="button" that calls markAsRead on click.
    const notifItem = el.querySelector('app-notification-item');
    const clickTarget = notifItem?.querySelector('[role="button"]') as HTMLElement;
    clickTarget.click();
    expect(storeMock.markAsRead).toHaveBeenCalledWith('n1');
  });

  it('calls store.markAllAsRead when "Mark all as read" button is clicked', async () => {
    const notifs = [mockNotif({ id: 'n1', read: false })];
    const { fixture, storeMock } = setup(notifs);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const btn = el.querySelector('[data-testid="mark-all-read-btn"]') as HTMLButtonElement;
    btn.click();
    expect(storeMock.markAllAsRead).toHaveBeenCalled();
  });
});
