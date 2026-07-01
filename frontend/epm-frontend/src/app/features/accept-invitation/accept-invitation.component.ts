import {
  Component,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { InvitationService } from '../registration/invitation.service';

// ---------------------------------------------------------------------------
// Pure validator — easy to unit-test, zero side-effects
// ---------------------------------------------------------------------------

export function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const password        = group.get('password')?.value as string | undefined;
  const confirmPassword = group.get('confirmPassword')?.value as string | undefined;
  if (password && confirmPassword && password !== confirmPassword) {
    return { passwordMismatch: true };
  }
  return null;
}

// TODO: i18n — all user-facing strings are in English. Add Transloco once
// translation keys are agreed with the i18n team.

@Component({
  selector: 'app-accept-invitation',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="ai-page min-h-screen flex items-center justify-center p-4">
      <div class="ai-card w-full max-w-md rounded-2xl p-8 flex flex-col gap-6">

        <!-- Header -->
        <div class="flex flex-col gap-1">
          <h1 class="text-2xl font-semibold" style="color: var(--color-text-primary); font-family: 'Outfit', sans-serif;">
            Complete your registration
          </h1>
          <p class="text-sm" style="color: var(--color-text-muted);">
            Set your name and password to activate your account.
          </p>
        </div>

        <!-- Invalid link -->
        @if (invalidLink) {
          <div class="ai-error-banner rounded-xl px-4 py-3 text-sm flex items-start gap-2" role="alert" data-testid="invalid-link-banner">
            <span class="material-symbols-rounded text-base shrink-0 mt-0.5">link_off</span>
            <span>This invitation link is invalid. Please request a new invitation.</span>
          </div>
        }

        <!-- Success state -->
        @if (success) {
          <div class="ai-success rounded-xl px-4 py-3 text-sm flex items-center gap-2" role="status">
            <span class="material-symbols-rounded text-base">check_circle</span>
            Account created! Redirecting to login&hellip;
          </div>
        }

        <!-- Error banner -->
        @if (error && !success && !invalidLink) {
          <div
            class="ai-error-banner rounded-xl px-4 py-3 text-sm flex items-start gap-2"
            role="alert"
            data-testid="error-banner"
          >
            <span class="material-symbols-rounded text-base shrink-0 mt-0.5">error</span>
            <span>{{ error }}</span>
          </div>
        }

        <!-- Form — only shown when link is valid and not yet successful -->
        @if (!invalidLink && !success) {
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="flex flex-col gap-4">

            <!-- Email — read-only display -->
            <div class="flex flex-col gap-1.5">
              <label class="ai-label text-sm font-medium" for="email-display">Email</label>
              <div
                id="email-display"
                class="ai-field-readonly w-full px-4 py-3 rounded-xl text-sm"
                aria-readonly="true"
                data-testid="email-display"
              >{{ email }}</div>
            </div>

            <!-- First name / Last name row -->
            <div class="grid grid-cols-2 gap-3">
              <div class="flex flex-col gap-1.5">
                <label class="ai-label text-sm font-medium" for="firstName">First name</label>
                <input
                  id="firstName"
                  type="text"
                  formControlName="firstName"
                  placeholder="Alice"
                  autocomplete="given-name"
                  class="ai-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
                />
                @if (form.controls.firstName.touched && form.controls.firstName.hasError('required')) {
                  <span class="ai-field-error text-xs" role="alert">First name is required</span>
                }
              </div>

              <div class="flex flex-col gap-1.5">
                <label class="ai-label text-sm font-medium" for="lastName">Last name</label>
                <input
                  id="lastName"
                  type="text"
                  formControlName="lastName"
                  placeholder="Smith"
                  autocomplete="family-name"
                  class="ai-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
                />
                @if (form.controls.lastName.touched && form.controls.lastName.hasError('required')) {
                  <span class="ai-field-error text-xs" role="alert">Last name is required</span>
                }
              </div>
            </div>

            <!-- Password -->
            <div class="flex flex-col gap-1.5">
              <label class="ai-label text-sm font-medium" for="password">Password</label>
              <input
                id="password"
                type="password"
                formControlName="password"
                placeholder="Min. 8 characters"
                autocomplete="new-password"
                class="ai-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
              />
              @if (form.controls.password.touched) {
                @if (form.controls.password.hasError('required')) {
                  <span class="ai-field-error text-xs" role="alert">Password is required</span>
                } @else if (form.controls.password.hasError('minlength')) {
                  <span class="ai-field-error text-xs" role="alert">Password must be at least 8 characters</span>
                }
              }
            </div>

            <!-- Confirm password -->
            <div class="flex flex-col gap-1.5">
              <label class="ai-label text-sm font-medium" for="confirmPassword">Confirm password</label>
              <input
                id="confirmPassword"
                type="password"
                formControlName="confirmPassword"
                placeholder="Repeat your password"
                autocomplete="new-password"
                class="ai-field w-full px-4 py-3 rounded-xl text-sm focus:outline-none transition-all duration-200"
              />
              @if (form.controls.confirmPassword.touched && form.errors?.['passwordMismatch']) {
                <span class="ai-field-error text-xs" role="alert">Passwords do not match</span>
              }
            </div>

            <!-- Submit button -->
            <button
              type="submit"
              [disabled]="loading"
              class="ai-submit w-full py-3 rounded-xl text-sm font-semibold transition-all duration-200 mt-2"
            >
              @if (loading) {
                <span class="flex items-center justify-center gap-2">
                  <span class="ai-spinner"></span>
                  Creating account&hellip;
                </span>
              } @else {
                Create account
              }
            </button>

          </form>
        }

      </div>
    </div>

    <style>
      .ai-page {
        background: var(--color-bg-base);
      }

      .ai-card {
        background: var(--color-bg-elevated);
        border: 1px solid var(--color-border);
        box-shadow: 0 4px 24px color-mix(in oklch, var(--color-shadow, #000) 12%, transparent);
      }

      .ai-label {
        color: var(--color-text-secondary);
      }

      .ai-field {
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border);
        color: var(--color-text-primary);
        font-family: 'Outfit', sans-serif;
      }

      .ai-field::placeholder {
        color: var(--color-text-muted);
      }

      .ai-field:focus {
        border-color: var(--color-accent);
        box-shadow: 0 0 0 3px var(--color-accent-subtle), 0 0 0 1px var(--color-accent);
      }

      .ai-field-readonly {
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border);
        color: var(--color-text-muted);
        font-family: 'Outfit', sans-serif;
        cursor: not-allowed;
      }

      .ai-field-error {
        color: var(--color-danger);
      }

      .ai-submit {
        background: var(--color-accent);
        color: oklch(1 0 0);
        border: none;
        cursor: pointer;
      }

      .ai-submit:hover:not(:disabled) {
        background: color-mix(in oklch, var(--color-accent) 85%, #000);
      }

      .ai-submit:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }

      .ai-success {
        background: color-mix(in oklch, var(--color-success) 10%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-success) 30%, transparent);
        color: var(--color-success);
      }

      .ai-error-banner {
        background: color-mix(in oklch, var(--color-danger) 8%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-danger) 25%, transparent);
        color: var(--color-danger);
      }

      .ai-spinner {
        display: inline-block;
        width: 1rem;
        height: 1rem;
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-top-color: #fff;
        border-radius: 50%;
        animation: ai-spin 0.7s linear infinite;
      }

      @keyframes ai-spin {
        to { transform: rotate(360deg); }
      }
    </style>
  `,
})
export class AcceptInvitationComponent {
  private readonly route             = inject(ActivatedRoute);
  private readonly invitationService = inject(InvitationService);
  private readonly oauthService      = inject(OAuthService);
  private readonly cdr               = inject(ChangeDetectorRef);
  private readonly fb                = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group(
    {
      firstName:       ['', Validators.required],
      lastName:        ['', Validators.required],
      password:        ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordMatchValidator },
  );

  /** Token read from query param — used only in submit(), not a form field */
  readonly token: string | null;
  /** Email read from query param — displayed read-only */
  readonly email: string | null;

  invalidLink = false;
  loading     = false;
  success     = false;
  error: string | null = null;

  constructor() {
    this.token = this.route.snapshot.queryParamMap.get('token');
    this.email = this.route.snapshot.queryParamMap.get('email');

    if (!this.token) {
      this.invalidLink = true;
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.error   = null;

    const { firstName, lastName, password } = this.form.getRawValue();

    this.invitationService
      .acceptInvitation(this.token!, firstName, lastName, password)
      .subscribe({
        next: () => {
          this.success = true;
          this.loading = false;
          this.cdr.markForCheck();
          setTimeout(() => this.oauthService.initCodeFlow(), 2000);
        },
        error: (err: { status?: number }) => {
          this.loading = false;

          if (err.status === 410) {
            this.error = 'This invitation link has expired.';
          } else if (err.status === 409) {
            this.error = 'This invitation has already been accepted.';
          } else {
            this.error = 'Something went wrong. Please try again.';
          }

          this.cdr.markForCheck();
        },
      });
  }
}
