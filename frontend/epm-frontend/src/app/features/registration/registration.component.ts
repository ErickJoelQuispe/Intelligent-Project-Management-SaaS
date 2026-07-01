import {
  Component,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { OAuthService } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';

// TODO: i18n — all user-facing strings are in English. Add Transloco once
// translation keys are agreed with the i18n team.

@Component({
  selector: 'app-registration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="reg-page min-h-screen flex items-center justify-center p-4">
      <div class="reg-card w-full max-w-md rounded-2xl p-8 flex flex-col gap-6">

        <!-- Header -->
        <div class="flex flex-col gap-1">
          <h1 class="text-2xl font-semibold" style="color: var(--color-text-primary); font-family: 'Outfit', sans-serif;">
            Create your account
          </h1>
          <p class="text-sm" style="color: var(--color-text-muted);">
            Join your team on EPM.
          </p>
        </div>

        <!-- Success state -->
        @if (success) {
          <div class="reg-success rounded-xl px-4 py-3 text-sm flex items-center gap-2" role="status">
            <span class="material-symbols-rounded text-base">check_circle</span>
            Account created! Redirecting to login&hellip;
          </div>
        }

        <!-- Error banner -->
        @if (error && !success) {
          <div
            class="reg-error-banner rounded-xl px-4 py-3 text-sm flex items-start gap-2"
            role="alert"
            data-testid="error-banner"
          >
            <span class="material-symbols-rounded text-base shrink-0 mt-0.5">error</span>
            <span>{{ error }}</span>
          </div>
        }

        <!-- Registration form -->
        @if (!success) {
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="flex flex-col gap-4">

            <!-- First name / Last name row -->
            <div class="grid grid-cols-2 gap-3">
              <div class="flex flex-col gap-1.5">
                <label class="reg-label text-sm font-medium" for="firstName">First name</label>
                <input
                  id="firstName"
                  type="text"
                  formControlName="firstName"
                  placeholder="Alice"
                  autocomplete="given-name"
                  class="reg-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
                />
                @if (form.controls.firstName.touched && form.controls.firstName.hasError('required')) {
                  <span class="reg-field-error text-xs" role="alert">First name is required</span>
                }
              </div>

              <div class="flex flex-col gap-1.5">
                <label class="reg-label text-sm font-medium" for="lastName">Last name</label>
                <input
                  id="lastName"
                  type="text"
                  formControlName="lastName"
                  placeholder="Smith"
                  autocomplete="family-name"
                  class="reg-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
                />
                @if (form.controls.lastName.touched && form.controls.lastName.hasError('required')) {
                  <span class="reg-field-error text-xs" role="alert">Last name is required</span>
                }
              </div>
            </div>

            <!-- Email -->
            <div class="flex flex-col gap-1.5">
              <label class="reg-label text-sm font-medium" for="email">Email</label>
              <input
                id="email"
                type="email"
                formControlName="email"
                placeholder="alice@example.com"
                autocomplete="email"
                class="reg-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
              />
              @if (form.controls.email.touched) {
                @if (form.controls.email.hasError('required')) {
                  <span class="reg-field-error text-xs" role="alert">Email is required</span>
                } @else if (form.controls.email.hasError('email')) {
                  <span class="reg-field-error text-xs" role="alert">Enter a valid email address</span>
                } @else if (form.controls.email.hasError('emailTaken')) {
                  <span class="reg-field-error text-xs" role="alert">This email is already registered</span>
                }
              }
            </div>

            <!-- Password -->
            <div class="flex flex-col gap-1.5">
              <label class="reg-label text-sm font-medium" for="password">Password</label>
              <input
                id="password"
                type="password"
                formControlName="password"
                placeholder="Min. 8 characters"
                autocomplete="new-password"
                class="reg-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
              />
              @if (form.controls.password.touched) {
                @if (form.controls.password.hasError('required')) {
                  <span class="reg-field-error text-xs" role="alert">Password is required</span>
                } @else if (form.controls.password.hasError('minlength')) {
                  <span class="reg-field-error text-xs" role="alert">Password must be at least 8 characters</span>
                }
              }
            </div>

            <!-- Submit button -->
            <button
              type="submit"
              [disabled]="loading"
              class="reg-submit w-full py-3 rounded-xl text-sm font-semibold transition-all duration-200 mt-2"
            >
              @if (loading) {
                <span class="flex items-center justify-center gap-2">
                  <span class="reg-spinner"></span>
                  Creating account&hellip;
                </span>
              } @else {
                Create account
              }
            </button>

            <!-- Sign in link -->
            <p class="text-center text-sm" style="color: var(--color-text-muted);">
              Already have an account?
              <button
                type="button"
                (click)="signIn()"
                class="reg-sign-in-link font-medium"
                style="color: var(--color-accent);"
              >
                Sign in
              </button>
            </p>

          </form>
        }

      </div>
    </div>

    <style>
      .reg-page {
        background: var(--color-bg-base);
      }

      .reg-card {
        background: var(--color-bg-elevated);
        border: 1px solid var(--color-border);
        box-shadow: 0 4px 24px color-mix(in oklch, var(--color-shadow, #000) 12%, transparent);
      }

      .reg-label {
        color: var(--color-text-secondary);
      }

      .reg-field {
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border);
        color: var(--color-text-primary);
        font-family: 'Outfit', sans-serif;
      }

      .reg-field::placeholder {
        color: var(--color-text-muted);
      }

      .reg-field:focus {
        border-color: var(--color-accent);
        box-shadow: 0 0 0 3px var(--color-accent-subtle), 0 0 0 1px var(--color-accent);
      }

      .reg-field-error {
        color: var(--color-danger);
      }

      .reg-submit {
        background: var(--color-accent);
        color: oklch(1 0 0);
        border: none;
        cursor: pointer;
      }

      .reg-submit:hover:not(:disabled) {
        background: color-mix(in oklch, var(--color-accent) 85%, #000);
      }

      .reg-submit:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }

      .reg-success {
        background: color-mix(in oklch, var(--color-success) 10%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-success) 30%, transparent);
        color: var(--color-success);
      }

      .reg-error-banner {
        background: color-mix(in oklch, var(--color-danger) 8%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-danger) 25%, transparent);
        color: var(--color-danger);
      }

      .reg-sign-in-link {
        background: none;
        border: none;
        cursor: pointer;
        padding: 0;
        font-family: inherit;
        font-size: inherit;
      }

      .reg-sign-in-link:hover {
        text-decoration: underline;
      }

      .reg-spinner {
        display: inline-block;
        width: 1rem;
        height: 1rem;
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-top-color: #fff;
        border-radius: 50%;
        animation: spin 0.7s linear infinite;
      }

      @keyframes spin {
        to { transform: rotate(360deg); }
      }
    </style>
  `,
})
export class RegistrationComponent {
  private readonly http        = inject(HttpClient);
  private readonly oauthService = inject(OAuthService);
  private readonly cdr          = inject(ChangeDetectorRef);
  private readonly fb           = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName:  ['', Validators.required],
    email:     ['', [Validators.required, Validators.email]],
    password:  ['', [Validators.required, Validators.minLength(8)]],
  });

  loading = false;
  success = false;
  error: string | null = null;

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.error   = null;

    const { email, password, firstName, lastName } = this.form.getRawValue();

    this.http
      .post(`${environment.apiBaseUrl}/auth/register`, { email, password, firstName, lastName })
      .subscribe({
        next: () => {
          this.success = true;
          this.loading = false;
          this.cdr.markForCheck();
          // Redirect to Keycloak login after a brief confirmation moment
          setTimeout(() => this.oauthService.initCodeFlow(), 2000);
        },
        error: (err: HttpErrorResponse) => {
          this.loading = false;

          if (err.status === 409) {
            this.form.controls.email.setErrors({ emailTaken: true });
            this.form.controls.email.markAsTouched();
          } else if (err.status >= 400 && err.status < 500) {
            this.error = 'Please check your input and try again.';
          } else {
            this.error = 'Registration failed. Please try again.';
          }

          this.cdr.markForCheck();
        },
      });
  }

  signIn(): void {
    this.oauthService.initCodeFlow();
  }
}
