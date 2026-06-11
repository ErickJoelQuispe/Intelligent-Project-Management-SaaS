import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { NotificationPreferencesComponent } from './notification-preferences.component';
import { NotificationPreferencesStore } from '../../store/notification-preferences.store';
import { NotificationPreferencesService } from '../../services/notification-preferences.service';
import { NotificationPreference } from '../../models/notification.model';

const mockPreferences: NotificationPreference[] = [
  { eventType: 'TASK_CREATED', channel: 'IN_APP', enabled: true },
  { eventType: 'TASK_CREATED', channel: 'EMAIL', enabled: false },
  { eventType: 'TASK_ASSIGNED', channel: 'IN_APP', enabled: true },
  { eventType: 'TASK_ASSIGNED', channel: 'EMAIL', enabled: true },
];

function createStoreMock(preferences: NotificationPreference[] = [], loading = false) {
  return {
    preferences: signal(preferences),
    loading: signal(loading),
    error: signal<string | null>(null),
    loadPreferences: vi.fn(),
    updatePreference: vi.fn(),
  };
}

describe('NotificationPreferencesComponent', () => {
  function setup(preferences: NotificationPreference[] = [], loading = false): {
    fixture: ComponentFixture<NotificationPreferencesComponent>;
    storeMock: ReturnType<typeof createStoreMock>;
  } {
    const storeMock = createStoreMock(preferences, loading);

    TestBed.configureTestingModule({
      imports: [NotificationPreferencesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimations(),
        { provide: NotificationPreferencesStore, useValue: storeMock },
        { provide: NotificationPreferencesService, useValue: {} },
      ],
    });

    const fixture = TestBed.createComponent(NotificationPreferencesComponent);
    fixture.detectChanges();
    return { fixture, storeMock };
  }

  afterEach(() => TestBed.resetTestingModule());

  it('should create the component', () => {
    const { fixture } = setup(mockPreferences);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('calls store.loadPreferences() on ngOnInit', () => {
    const { storeMock } = setup(mockPreferences);
    expect(storeMock.loadPreferences).toHaveBeenCalledOnce();
  });

  it('shows loading indicator when store.loading() is true', async () => {
    const { fixture } = setup([], true);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const spinner = el.querySelector('[data-testid="loading-spinner"]');
    expect(spinner).toBeTruthy();
  });

  it('hides loading indicator when store.loading() is false', async () => {
    const { fixture } = setup(mockPreferences, false);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const spinner = el.querySelector('[data-testid="loading-spinner"]');
    expect(spinner).toBeFalsy();
  });

  it('renders preference rows for each preference item', async () => {
    const { fixture } = setup(mockPreferences);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('[data-testid^="pref-row-"]');
    expect(rows.length).toBe(4);
  });

  it('renders the event type and channel for each preference', async () => {
    const { fixture } = setup([mockPreferences[0]]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    // Template maps TASK_CREATED → 'Task created' via eventLabel()
    expect(el.textContent).toContain('Task created');
    // Template maps IN_APP channel → 'In-app' via badge display
    expect(el.textContent).toContain('In-app');
  });

  it('onToggleChange() calls store.updatePreference with toggled value (enabled → false)', () => {
    const { fixture, storeMock } = setup(mockPreferences);
    const component = fixture.componentInstance;

    // Simulate the (change) output from mat-slide-toggle
    const pref = mockPreferences[0]; // TASK_CREATED / IN_APP / enabled=true
    component.onToggle(pref, { checked: false } as any);

    expect(storeMock.updatePreference).toHaveBeenCalledWith('TASK_CREATED', 'IN_APP', false);
  });

  it('onToggleChange() calls store.updatePreference with enabled=true when toggled on', () => {
    const { fixture, storeMock } = setup(mockPreferences);
    const component = fixture.componentInstance;

    // TASK_CREATED / EMAIL is enabled=false → toggle to true
    const pref = mockPreferences[1]; // TASK_CREATED / EMAIL / enabled=false
    component.onToggle(pref, { checked: true } as any);

    expect(storeMock.updatePreference).toHaveBeenCalledWith('TASK_CREATED', 'EMAIL', true);
  });
});
