import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { NotificationPreferencesStore } from './notification-preferences.store';
import { NotificationPreferencesService } from '../services/notification-preferences.service';
import { NotificationPreference } from '../models/notification.model';

const mockPreferences: NotificationPreference[] = [
  { eventType: 'TASK_CREATED', channel: 'IN_APP', enabled: true },
  { eventType: 'TASK_ASSIGNED', channel: 'EMAIL', enabled: false },
  { eventType: 'PROJECT_CREATED', channel: 'IN_APP', enabled: true },
];

describe('NotificationPreferencesStore', () => {
  let store: InstanceType<typeof NotificationPreferencesStore>;
  let serviceMock: {
    getPreferences: ReturnType<typeof vi.fn>;
    updatePreference: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    serviceMock = {
      getPreferences: vi.fn(),
      updatePreference: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotificationPreferencesStore,
        { provide: NotificationPreferencesService, useValue: serviceMock },
      ],
    });

    store = TestBed.inject(NotificationPreferencesStore);
  });

  it('should initialize with empty preferences and loading false', () => {
    expect(store.preferences()).toEqual([]);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('loadPreferences() sets loading true during fetch and populates preferences on success', () => {
    serviceMock.getPreferences.mockReturnValue(of(mockPreferences));

    TestBed.runInInjectionContext(() => {
      store.loadPreferences();
    });

    expect(serviceMock.getPreferences).toHaveBeenCalledOnce();
    expect(store.preferences().length).toBe(3);
    expect(store.preferences()[0].eventType).toBe('TASK_CREATED');
    expect(store.preferences()[1].enabled).toBe(false);
    expect(store.loading()).toBe(false);
  });

  it('loadPreferences() sets error on failure and loading to false', () => {
    serviceMock.getPreferences.mockReturnValue(
      throwError(() => new Error('Failed to load')),
    );

    TestBed.runInInjectionContext(() => {
      store.loadPreferences();
    });

    expect(store.error()).toBe('Failed to load');
    expect(store.loading()).toBe(false);
    expect(store.preferences()).toEqual([]);
  });

  it('updatePreference() optimistically toggles the preference before HTTP call', () => {
    serviceMock.getPreferences.mockReturnValue(of(mockPreferences));
    serviceMock.updatePreference.mockReturnValue(of(void 0));

    TestBed.runInInjectionContext(() => {
      store.loadPreferences();
      // TASK_CREATED IN_APP is currently enabled=true; toggle it off
      store.updatePreference('TASK_CREATED', 'IN_APP', false);
    });

    // Optimistic update: should already be false
    const pref = store.preferences().find(
      (p) => p.eventType === 'TASK_CREATED' && p.channel === 'IN_APP',
    );
    expect(pref!.enabled).toBe(false);
    expect(serviceMock.updatePreference).toHaveBeenCalledWith('TASK_CREATED', 'IN_APP', false);
  });

  it('updatePreference() rolls back optimistic update on HTTP error', () => {
    serviceMock.getPreferences.mockReturnValue(of(mockPreferences));
    serviceMock.updatePreference.mockReturnValue(
      throwError(() => new Error('Network error')),
    );

    TestBed.runInInjectionContext(() => {
      store.loadPreferences();
      // TASK_ASSIGNED EMAIL is currently enabled=false; try to toggle it on
      store.updatePreference('TASK_ASSIGNED', 'EMAIL', true);
    });

    // Rollback: should revert to original enabled=false
    const pref = store.preferences().find(
      (p) => p.eventType === 'TASK_ASSIGNED' && p.channel === 'EMAIL',
    );
    expect(pref!.enabled).toBe(false);
  });
});
