import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { SessionsStore } from './sessions.store';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { OAuthService } from 'angular-oauth2-oidc';
import { MatDialog } from '@angular/material/dialog';
import { UserSession } from '../../../core/models/user-session.model';

const MOCK_SESSIONS: UserSession[] = [
  {
    sessionId: 'sid-1',
    ipAddress: '192.168.1.1',
    started: '2024-11-14T20:00:00Z',
    lastAccess: '2024-11-14T20:16:40Z',
  },
  {
    sessionId: 'sid-2',
    ipAddress: '10.0.0.2',
    started: '2024-11-14T20:33:20Z',
    lastAccess: '2024-11-14T20:50:00Z',
  },
];

function buildAuthApiMock(overrides: Partial<{ getSessions: unknown; revokeSession: unknown }> = {}) {
  return {
    disableAccount: vi.fn().mockReturnValue(of(undefined)),
    getSessions: vi.fn().mockReturnValue(of(MOCK_SESSIONS)),
    revokeSession: vi.fn().mockReturnValue(of(undefined)),
    ...overrides,
  };
}

describe('SessionsStore', () => {
  let store: InstanceType<typeof SessionsStore>;
  let authApiMock: ReturnType<typeof buildAuthApiMock>;
  let oauthMock: { getIdentityClaims: ReturnType<typeof vi.fn> };
  let dialogMock: { open: ReturnType<typeof vi.fn> };

  function setup(authApiOverrides: Partial<{ getSessions: unknown; revokeSession: unknown }> = {}) {
    authApiMock = buildAuthApiMock(authApiOverrides);
    oauthMock = { getIdentityClaims: vi.fn().mockReturnValue({ sid: 'sid-1' }) };
    dialogMock = {
      open: vi.fn().mockReturnValue({ afterClosed: () => of(true) }),
    };

    TestBed.configureTestingModule({
      providers: [
        SessionsStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthApiService, useValue: authApiMock },
        { provide: OAuthService, useValue: oauthMock },
        { provide: MatDialog, useValue: dialogMock },
      ],
    });

    store = TestBed.inject(SessionsStore);
  }

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── loadSessions: sets sessions signal with returned data ──────────────────

  it('loadSessions() sets sessions signal with data from API', async () => {
    setup();

    await store.loadSessions();

    expect(store.sessions()).toHaveLength(2);
    expect(store.sessions()[0].sessionId).toBe('sid-1');
    expect(store.sessions()[1].ipAddress).toBe('10.0.0.2');
  });

  // ── TRIANGULATE: empty array → sessions signal is [] ──────────────────────

  it('loadSessions() sets sessions signal to [] when API returns empty list', async () => {
    setup({ getSessions: vi.fn().mockReturnValue(of([])) });

    await store.loadSessions();

    expect(store.sessions()).toHaveLength(0);
  });

  // ── currentSessionId: returns sid claim from JWT ───────────────────────────

  it('currentSessionId() returns sid claim from JWT identity claims', () => {
    setup();

    expect(store.currentSessionId()).toBe('sid-1');
  });

  // ── TRIANGULATE: currentSessionId returns null when no sid claim ──────────

  it('currentSessionId() returns null when sid claim is absent', () => {
    setup();
    oauthMock.getIdentityClaims.mockReturnValue({});

    expect(store.currentSessionId()).toBeNull();
  });

  // ── revokeSession: non-current optimistically removes session ─────────────

  it('revokeSession() removes session from list optimistically', async () => {
    setup();
    await store.loadSessions();
    expect(store.sessions()).toHaveLength(2);

    await store.revokeSession('sid-2');

    expect(store.sessions().find(s => s.sessionId === 'sid-2')).toBeUndefined();
    expect(authApiMock.revokeSession).toHaveBeenCalledWith('sid-2');
  });

  // ── TRIANGULATE: revokeSession on error restores list and sets error ───────

  it('revokeSession() on error restores session list and sets error signal', async () => {
    setup({ revokeSession: vi.fn().mockReturnValue(throwError(() => new Error('503 unavailable'))) });
    await store.loadSessions();
    const originalLength = store.sessions().length;

    await store.revokeSession('sid-2');

    expect(store.sessions()).toHaveLength(originalLength);
    expect(store.error()).toBeTruthy();
    expect(store.error()).toContain('503 unavailable');
  });
});
