import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RegistrationComponent } from './registration.component';
import { OAuthService } from 'angular-oauth2-oidc';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function setup() {
  const oauthServiceMock = {
    initCodeFlow: vi.fn(),
  };

  TestBed.configureTestingModule({
    imports: [RegistrationComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: OAuthService, useValue: oauthServiceMock },
    ],
  });

  const fixture: ComponentFixture<RegistrationComponent> = TestBed.createComponent(RegistrationComponent);
  const component = fixture.componentInstance;
  const httpMock = TestBed.inject(HttpTestingController);
  const compiled = fixture.nativeElement as HTMLElement;
  fixture.detectChanges();

  return { fixture, component, httpMock, compiled, oauthServiceMock };
}

// ---------------------------------------------------------------------------
// Suite 1: Form structure
// ---------------------------------------------------------------------------

describe('RegistrationComponent — form structure', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders a form with email, password, firstName, and lastName fields', () => {
    const { compiled } = setup();

    const emailInput    = compiled.querySelector('input[formControlName="email"]');
    const passwordInput = compiled.querySelector('input[formControlName="password"]');
    const firstNameInput = compiled.querySelector('input[formControlName="firstName"]');
    const lastNameInput  = compiled.querySelector('input[formControlName="lastName"]');

    expect(emailInput).not.toBeNull();
    expect(passwordInput).not.toBeNull();
    expect(firstNameInput).not.toBeNull();
    expect(lastNameInput).not.toBeNull();
  });

  it('renders a submit button', () => {
    const { compiled } = setup();
    const submitBtn = compiled.querySelector('button[type="submit"]');
    expect(submitBtn).not.toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Suite 2: Client-side validation
// ---------------------------------------------------------------------------

describe('RegistrationComponent — client-side validation', () => {
  afterEach(() => vi.restoreAllMocks());

  it('does not call the API when form is submitted with all fields empty', () => {
    const { fixture, component, httpMock } = setup();

    // All fields are empty (form is invalid by default)
    component.submit();
    fixture.detectChanges();

    httpMock.expectNone('http://localhost:8080/api/v1/auth/register');
    httpMock.verify();
  });

  it('marks all controls as touched when empty form is submitted', () => {
    const { component } = setup();

    component.submit();

    const { email, password, firstName, lastName } = component.form.controls;
    expect(email.touched).toBe(true);
    expect(password.touched).toBe(true);
    expect(firstName.touched).toBe(true);
    expect(lastName.touched).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Suite 3: Successful registration (201)
// ---------------------------------------------------------------------------

describe('RegistrationComponent — 201 success', () => {
  afterEach(() => vi.restoreAllMocks());

  it('POSTs to the correct URL with firstName, lastName, email, password on valid submit', () => {
    const { fixture, component, httpMock } = setup();

    component.form.setValue({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });
    req.flush(null, { status: 201, statusText: 'Created' });
    httpMock.verify();
  });

  it('shows success message after 201 response', () => {
    const { fixture, component, httpMock, compiled } = setup();

    component.form.setValue({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush(null, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(component.success).toBe(true);
    expect(compiled.textContent).toContain('Account created');
    httpMock.verify();
  });

  it('does NOT send an Authorization header', () => {
    const { fixture, component, httpMock } = setup();

    component.form.setValue({
      email: 'bob@example.com',
      password: 'password123',
      firstName: 'Bob',
      lastName: 'Jones',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(null, { status: 201, statusText: 'Created' });
    httpMock.verify();
  });
});

// ---------------------------------------------------------------------------
// Suite 4: 409 — email already registered
// ---------------------------------------------------------------------------

describe('RegistrationComponent — 409 email taken', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets emailTaken error on the email control when server returns 409', () => {
    const { fixture, component, httpMock } = setup();

    component.form.setValue({
      email: 'taken@example.com',
      password: 'securepass',
      firstName: 'Test',
      lastName: 'User',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush({ message: 'Email already registered' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(component.form.controls.email.errors?.['emailTaken']).toBe(true);
    httpMock.verify();
  });

  it('shows inline email-taken error text in the DOM after 409', () => {
    const { fixture, component, httpMock, compiled } = setup();

    component.form.setValue({
      email: 'taken@example.com',
      password: 'securepass',
      firstName: 'Test',
      lastName: 'User',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush({ message: 'Email already registered' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(compiled.textContent).toContain('already registered');
    httpMock.verify();
  });
});

// ---------------------------------------------------------------------------
// Suite 5: 5xx — generic error banner
// ---------------------------------------------------------------------------

describe('RegistrationComponent — 5xx server error', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets the error property when the server returns 500', () => {
    const { fixture, component, httpMock } = setup();

    component.form.setValue({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush({ message: 'Internal Server Error' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(component.error).toBeTruthy();
    httpMock.verify();
  });

  it('shows error banner in DOM after 500 response', () => {
    const { fixture, component, httpMock, compiled } = setup();

    component.form.setValue({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush({ message: 'Internal Server Error' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const errorBanner = compiled.querySelector('[data-testid="error-banner"]');
    expect(errorBanner).not.toBeNull();
    httpMock.verify();
  });
});

// ---------------------------------------------------------------------------
// Suite 6: Loading state
// ---------------------------------------------------------------------------

describe('RegistrationComponent — loading state', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sets loading=true while the request is in flight', () => {
    const { fixture, component, httpMock } = setup();

    component.form.setValue({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });

    component.submit();
    fixture.detectChanges();

    // Before flushing the response — loading must be true
    expect(component.loading).toBe(true);

    // Cleanup
    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush(null, { status: 201, statusText: 'Created' });
    httpMock.verify();
  });

  it('resets loading=false after the request completes', () => {
    const { fixture, component, httpMock } = setup();

    component.form.setValue({
      email: 'alice@example.com',
      password: 'securepass',
      firstName: 'Alice',
      lastName: 'Smith',
    });

    component.submit();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush(null, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(component.loading).toBe(false);
    httpMock.verify();
  });
});
