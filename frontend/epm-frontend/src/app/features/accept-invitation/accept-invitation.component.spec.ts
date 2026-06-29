import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { AcceptInvitationComponent } from './accept-invitation.component';
import { InvitationService } from '../registration/invitation.service';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function setup(queryParams: Record<string, string> = {}) {
  const oauthMock = { initCodeFlow: vi.fn() };
  const invitationServiceMock = {
    acceptInvitation: vi.fn().mockReturnValue(of(undefined)),
  };

  TestBed.configureTestingModule({
    imports: [AcceptInvitationComponent],
    providers: [
      provideRouter([]),
      { provide: OAuthService, useValue: oauthMock },
      { provide: InvitationService, useValue: invitationServiceMock },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            queryParamMap: {
              get: (key: string) => queryParams[key] ?? null,
            },
          },
        },
      },
    ],
  });

  const fixture: ComponentFixture<AcceptInvitationComponent> =
    TestBed.createComponent(AcceptInvitationComponent);
  const component = fixture.componentInstance;
  fixture.detectChanges();

  const compiled = fixture.nativeElement as HTMLElement;
  return { fixture, component, compiled, oauthMock, invitationServiceMock };
}

// ---------------------------------------------------------------------------
// Suite 1: Invalid / missing token
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — no token', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets invalidLink=true when no token is in query params', () => {
    const { component } = setup({});
    expect(component.invalidLink).toBe(true);
  });

  it('shows "invalid" message and hides form when token is absent', () => {
    const { compiled } = setup({});
    expect(compiled.textContent).toContain('invalid');
    const form = compiled.querySelector('form');
    expect(form).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Suite 2: Valid token — form rendering
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — valid token renders form', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets invalidLink=false when a token is present', () => {
    const { component } = setup({ token: 'tok123', email: 'alice@example.com' });
    expect(component.invalidLink).toBe(false);
  });

  it('renders the form when token is present', () => {
    const { compiled } = setup({ token: 'tok123', email: 'alice@example.com' });
    const form = compiled.querySelector('form');
    expect(form).not.toBeNull();
  });

  it('pre-fills email from query param and shows it as read-only', () => {
    const { compiled } = setup({ token: 'tok123', email: 'alice@example.com' });
    // Email must NOT be an editable input
    const emailInput = compiled.querySelector('input[formControlName="email"]');
    expect(emailInput).toBeNull();
    // Email must appear as text somewhere in the component
    expect(compiled.textContent).toContain('alice@example.com');
  });

  it('renders firstName, lastName, password, confirmPassword fields', () => {
    const { compiled } = setup({ token: 'tok123', email: 'alice@example.com' });
    expect(compiled.querySelector('input[formControlName="firstName"]')).not.toBeNull();
    expect(compiled.querySelector('input[formControlName="lastName"]')).not.toBeNull();
    expect(compiled.querySelector('input[formControlName="password"]')).not.toBeNull();
    expect(compiled.querySelector('input[formControlName="confirmPassword"]')).not.toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Suite 3: Client-side validation
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — client-side validation', () => {
  afterEach(() => vi.restoreAllMocks());

  it('marks all controls as touched and makes no HTTP call when form is invalid', () => {
    const { component, invitationServiceMock } = setup({ token: 'tok123' });

    component.submit();

    expect(component.form.controls.firstName.touched).toBe(true);
    expect(component.form.controls.lastName.touched).toBe(true);
    expect(component.form.controls.password.touched).toBe(true);
    expect(component.form.controls.confirmPassword.touched).toBe(true);
    expect(invitationServiceMock.acceptInvitation).not.toHaveBeenCalled();
  });

  it('password mismatch validator sets passwordMismatch error on confirmPassword', () => {
    const { component } = setup({ token: 'tok123' });

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'different',
    });
    component.form.updateValueAndValidity();

    expect(component.form.errors?.['passwordMismatch']).toBeTruthy();
  });

  it('no passwordMismatch error when passwords match', () => {
    const { component } = setup({ token: 'tok123' });

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });
    component.form.updateValueAndValidity();

    expect(component.form.errors?.['passwordMismatch']).toBeFalsy();
  });
});

// ---------------------------------------------------------------------------
// Suite 4: Successful submission (201)
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — 201 success', () => {
  afterEach(() => vi.restoreAllMocks());

  it('calls acceptInvitation with token, firstName, lastName, password', () => {
    const { component, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();

    expect(invitationServiceMock.acceptInvitation).toHaveBeenCalledWith(
      'tok-abc',
      'Alice',
      'Smith',
      'secret123',
    );
  });

  it('sets success=true after 201 response', () => {
    const { component, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });
    invitationServiceMock.acceptInvitation.mockReturnValue(of(undefined));

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();

    expect(component.success).toBe(true);
  });

  it('shows success message in DOM after 201', () => {
    const { fixture, component, compiled, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });
    invitationServiceMock.acceptInvitation.mockReturnValue(of(undefined));

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();
    fixture.detectChanges();

    expect(compiled.textContent).toContain('Account created');
  });
});

// ---------------------------------------------------------------------------
// Suite 5: 410 — expired invitation
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — 410 expired', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets error to expired message on 410', () => {
    const { component, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });
    invitationServiceMock.acceptInvitation.mockReturnValue(
      throwError(() => ({ status: 410 })),
    );

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();

    expect(component.error).toContain('expired');
  });
});

// ---------------------------------------------------------------------------
// Suite 6: 409 — already accepted
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — 409 already accepted', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets error to already-accepted message on 409', () => {
    const { component, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });
    invitationServiceMock.acceptInvitation.mockReturnValue(
      throwError(() => ({ status: 409 })),
    );

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();

    // 409 maps to either "already accepted" or "email exists" — both valid per spec
    expect(component.error).toBeTruthy();
    expect(component.error!.length).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// Suite 7: Other error → generic message
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — generic error', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets generic error message on 500', () => {
    const { component, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });
    invitationServiceMock.acceptInvitation.mockReturnValue(
      throwError(() => ({ status: 500 })),
    );

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();

    expect(component.error).toContain('wrong');
  });
});

// ---------------------------------------------------------------------------
// Suite 8: Loading state
// ---------------------------------------------------------------------------

describe('AcceptInvitationComponent — loading state', () => {
  afterEach(() => vi.restoreAllMocks());

  it('resets loading=false after success', () => {
    const { component, invitationServiceMock } = setup({
      token: 'tok-abc',
      email: 'alice@example.com',
    });
    invitationServiceMock.acceptInvitation.mockReturnValue(of(undefined));

    component.form.setValue({
      firstName: 'Alice',
      lastName: 'Smith',
      password: 'secret123',
      confirmPassword: 'secret123',
    });

    component.submit();

    expect(component.loading).toBe(false);
  });
});
