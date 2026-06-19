import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
  effect,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatSlideToggleModule, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationPreferencesStore } from '../notifications/store/notification-preferences.store';
import { ProfileStore } from './store/profile.store';
import { UpdateProfileRequest } from '../../core/models/user-profile.model';
import { NotificationPreference, NotificationChannel, NotificationType } from '../notifications/models/notification.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { CardComponent } from '../../shared/components/card/card.component';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { ButtonComponent } from '../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner.component';

const EVENT_LABELS: Record<string, string> = {
  TASK_CREATED:             'Task created',
  TASK_ASSIGNED:            'Task assigned to you',
  TASK_STATUS_CHANGED:      'Task status changed',
  TASK_DELETED:             'Task deleted',
  PROJECT_CREATED:          'Project created',
  PROJECT_ARCHIVED:         'Project archived',
  TEAM_ASSIGNED_TO_PROJECT: 'Team assigned to project',
  MEMBER_JOINED_TEAM:       'Member joined team',
  MEMBER_LEFT_TEAM:         'Member left team',
};

const CHANNEL_VARIANT: Record<string, BadgeVariant> = {
  IN_APP: 'info',
  EMAIL:  'neutral',
};

type SettingsSection = 'profile' | 'notifications' | 'appearance';

@Component({
  selector: 'app-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatSlideToggleModule,
    PageHeaderComponent,
    CardComponent,
    BadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
    AvatarComponent,
    ButtonComponent,
    ErrorBannerComponent,
  ],
  template: `
    <app-page-header
      title="Settings"
      description="Manage your account and application preferences"
    />

    <div class="flex gap-0 h-full">

      <!-- ── Sidebar de secciones ── -->
      <nav class="w-56 shrink-0 border-r p-4 flex flex-col gap-1"
           style="border-color: color-mix(in oklch, var(--color-border) 60%, transparent); min-height: calc(100vh - 73px);">

        @for (section of sections; track section.id) {
          <button
            (click)="activeSection.set(section.id)"
            class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm
                   font-medium text-left w-full transition-all duration-150 cursor-pointer"
            [style.background]="activeSection() === section.id
              ? 'color-mix(in oklch, var(--color-accent) 12%, transparent)'
              : 'transparent'"
            [style.color]="activeSection() === section.id
              ? 'var(--color-text-primary)'
              : 'var(--color-text-secondary)'"
          >
            <span class="material-symbols-rounded text-lg"
                  [style.color]="activeSection() === section.id
                    ? 'var(--color-accent)'
                    : 'inherit'">
              {{ section.icon }}
            </span>
            {{ section.label }}
          </button>
        }
      </nav>

      <!-- ── Contenido de la sección activa ── -->
      <div class="flex-1 p-6 max-w-2xl">

        <!-- PROFILE -->
        @if (activeSection() === 'profile') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="text-text-primary font-semibold text-base"
                style="font-family: 'Outfit', sans-serif;">
              Profile
            </h2>

            <!-- Profile info card -->
            <app-card>
              @if (profileStore.loading()) {
                <app-spinner size="md" [full]="true" />
              } @else if (!isEditing()) {
                <!-- VIEW MODE -->
                <div class="flex flex-col gap-4">
                  <div class="flex items-center gap-4">
                    <app-avatar
                      [src]="profileStore.profile()?.avatarUrl"
                      [name]="displayName()"
                      size="lg"
                    />
                    <div class="flex flex-col gap-0.5">
                      <span class="text-text-primary font-semibold text-sm">
                        {{ displayName() }}
                      </span>
                      <span class="text-text-muted text-xs">
                        {{ profileStore.profile()?.email ?? userEmail() }}
                      </span>
                    </div>
                  </div>

                  @if (profileStore.profile()?.bio) {
                    <p class="text-text-muted text-sm leading-relaxed">
                      {{ profileStore.profile()!.bio }}
                    </p>
                  } @else {
                    <p class="text-text-disabled text-xs italic">No bio yet.</p>
                  }

                  @if (profileStore.profile()?.avatarUrl) {
                    <a
                      [href]="profileStore.profile()!.avatarUrl!"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="text-xs truncate"
                      style="color: var(--color-accent);"
                    >
                      {{ profileStore.profile()!.avatarUrl }}
                    </a>
                  }

                  @if (profileStore.error()) {
                    <app-error-banner [message]="profileStore.error()!" variant="inline" />
                  }

                  <div class="flex justify-end pt-1">
                    <app-button variant="secondary" size="sm" (click)="startEdit()">
                      <span class="material-symbols-rounded text-sm">edit</span>
                      Edit profile
                    </app-button>
                  </div>
                </div>
              } @else {
                <!-- EDIT MODE -->
                <form [formGroup]="editForm" (ngSubmit)="saveProfile()" novalidate
                      class="flex flex-col gap-5">

                  <div class="grid grid-cols-2 gap-4">
                    <!-- First name -->
                    <div class="flex flex-col gap-2">
                      <label class="text-sm font-semibold" for="firstName"
                             style="color: var(--color-text-secondary);">
                        First name
                        <span class="font-normal text-xs ml-1" style="color: var(--color-text-muted);">optional</span>
                      </label>
                      <input
                        id="firstName" type="text" formControlName="firstName"
                        placeholder="Jane"
                        class="w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none settings-field"
                        style="background: var(--color-bg-surface);
                               border: 1px solid var(--color-border);
                               color: var(--color-text-primary);
                               font-family: 'Outfit', sans-serif;"
                      />
                    </div>

                    <!-- Last name -->
                    <div class="flex flex-col gap-2">
                      <label class="text-sm font-semibold" for="lastName"
                             style="color: var(--color-text-secondary);">
                        Last name
                        <span class="font-normal text-xs ml-1" style="color: var(--color-text-muted);">optional</span>
                      </label>
                      <input
                        id="lastName" type="text" formControlName="lastName"
                        placeholder="Doe"
                        class="w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none settings-field"
                        style="background: var(--color-bg-surface);
                               border: 1px solid var(--color-border);
                               color: var(--color-text-primary);
                               font-family: 'Outfit', sans-serif;"
                      />
                    </div>
                  </div>

                  <!-- Bio -->
                  <div class="flex flex-col gap-2">
                      <label class="text-sm font-semibold" for="bio"
                             style="color: var(--color-text-secondary);">
                        Bio
                        <span class="font-normal text-xs ml-1" style="color: var(--color-text-muted);">optional</span>
                      </label>
                      <textarea
                        id="bio" formControlName="bio" rows="3"
                        placeholder="Tell us a bit about yourself..."
                        maxlength="2000"
                        class="w-full px-4 py-3 rounded-xl text-sm resize-none transition-all duration-200 focus:outline-none settings-field"
                        style="background: var(--color-bg-surface);
                               border: 1px solid var(--color-border);
                               color: var(--color-text-primary);
                               font-family: 'Outfit', sans-serif;"
                      ></textarea>
                  </div>

                  <!-- Avatar URL -->
                  <div class="flex flex-col gap-2">
                      <label class="text-sm font-semibold" for="avatarUrl"
                             style="color: var(--color-text-secondary);">
                        Avatar URL
                        <span class="font-normal text-xs ml-1" style="color: var(--color-text-muted);">optional</span>
                      </label>
                      <input
                        id="avatarUrl" type="url" formControlName="avatarUrl"
                        placeholder="https://example.com/avatar.png"
                        class="w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none settings-field"
                        style="background: var(--color-bg-surface);
                               border: 1px solid var(--color-border);
                               color: var(--color-text-primary);
                               font-family: 'Outfit', sans-serif;"
                      />
                  </div>

                  @if (profileStore.error()) {
                    <app-error-banner [message]="profileStore.error()!" variant="inline" />
                  }

                  <!-- Actions -->
                  <div class="flex items-center justify-end gap-3 pt-1">
                    <app-button
                      type="button"
                      variant="ghost"
                      size="sm"
                      (click)="cancelEdit()"
                    >
                      Cancel
                    </app-button>
                    <app-button
                      type="submit"
                      variant="primary"
                      size="sm"
                      [loading]="profileStore.saving()"
                      [disabled]="profileStore.saving()"
                    >
                      Save changes
                    </app-button>
                  </div>
                </form>
              }
            </app-card>

            <!-- Session card -->
            <app-card>
              <div class="flex flex-col gap-4">
                <h3 class="text-text-primary text-sm font-semibold">Session</h3>

                 <div class="flex items-center justify-between py-2"
                      style="border-bottom: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);">
                   <div class="flex flex-col gap-0.5">
                     <span class="text-text-primary text-sm">Active session</span>
                     <span class="text-text-muted text-xs">
                       Token expires in ~{{ tokenExpiresIn() }}
                     </span>
                   </div>
                   <span class="size-2 rounded-full animate-glow-pulse"
                         style="background: var(--color-success);
                                box-shadow: 0 0 6px color-mix(in oklch, var(--color-success) 60%, transparent);">
                   </span>
                 </div>

                <div class="flex justify-end pt-2">
                  <button (click)="logout()"
                          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm
                                 font-medium transition-all duration-150 cursor-pointer settings-logout"
                          style="background: color-mix(in oklch, var(--color-danger) 10%, transparent);
                                 color: var(--color-danger);
                                 border: 1px solid color-mix(in oklch, var(--color-danger) 25%, transparent);"
                          onmouseover="this.style.background='var(--color-danger)';this.style.color='white'"
                          onmouseout="this.style.background='color-mix(in oklch, var(--color-danger) 10%, transparent)';this.style.color='var(--color-danger)'"
                          >
                    <span class="material-symbols-rounded text-sm">logout</span>
                    Sign out
                  </button>
                </div>
              </div>
            </app-card>
          </div>
        }

        <!-- NOTIFICATIONS -->
        @if (activeSection() === 'notifications') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="text-text-primary font-semibold text-base"
                style="font-family: 'Outfit', sans-serif;">
              Notification preferences
            </h2>
            <p class="text-text-muted text-sm -mt-2">
              Choose which events trigger notifications and through which channel.
            </p>

            <app-card [noPadding]="true">
              @if (prefStore.loading()) {
                <app-spinner size="md" [full]="true" />
              } @else if (prefStore.preferences().length === 0) {
                <app-empty-state
                  icon="notifications_off"
                  title="No preferences configured"
                  size="sm"
                />
              } @else {
                @for (pref of prefStore.preferences();
                      track pref.eventType + pref.channel;
                      let last = $last) {
                  <div class="flex items-center justify-between px-6 py-4"
                       [class.border-b]="!last"
                       [style.border-color]="'color-mix(in oklch, var(--color-border) 50%, transparent)'">

                    <div class="flex flex-col gap-1.5">
                      <span class="text-text-primary text-sm font-medium">
                        {{ eventLabel(pref.eventType) }}
                      </span>
                      <app-badge [variant]="channelVariant(pref.channel)" size="sm">
                        {{ pref.channel === 'IN_APP' ? 'In-app' : 'Email' }}
                      </app-badge>
                    </div>

                    <mat-slide-toggle
                      [checked]="pref.enabled"
                      [attr.aria-label]="eventLabel(pref.eventType) + ' via ' + pref.channel"
                      (change)="onToggle(pref, $event)"
                    />
                  </div>
                }
              }
            </app-card>
          </div>
        }

        <!-- APPEARANCE -->
        @if (activeSection() === 'appearance') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="text-text-primary font-semibold text-base"
                style="font-family: 'Outfit', sans-serif;">
              Appearance
            </h2>

            <app-card>
              <div class="flex flex-col gap-5">

                <!-- Theme — solo dark por ahora -->
                <div class="flex items-center justify-between">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-text-primary text-sm font-medium">Theme</span>
                    <span class="text-text-muted text-xs">
                      Current: Dark (Deep Space)
                    </span>
                  </div>
                   <span class="px-3 py-1 rounded-full text-xs font-medium"
                         style="background: color-mix(in oklch, var(--color-accent) 12%, transparent);
                                color: var(--color-accent);
                                border: 1px solid color-mix(in oklch, var(--color-accent) 20%, transparent);">
                     Active
                   </span>
                </div>

                <div class="h-px" style="background: color-mix(in oklch, var(--color-border) 50%, transparent);"></div>

                <!-- Accent color preview -->
                <div class="flex flex-col gap-3">
                  <span class="text-text-primary text-sm font-medium">Accent color</span>
                  <div class="flex items-center gap-3">
                    <div class="size-8 rounded-lg cursor-pointer ring-2 ring-offset-2"
                         style="background: linear-gradient(135deg, var(--color-accent), var(--color-cyan));
                                ring-color: var(--color-accent);
                                ring-offset-color: var(--color-bg-base);">
                    </div>
                    <span class="text-text-muted text-xs">Violet → Cyan (default)</span>
                  </div>
                </div>

                <div class="h-px" style="background: color-mix(in oklch, var(--color-border) 50%, transparent);"></div>

                <!-- Font -->
                <div class="flex items-center justify-between">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-text-primary text-sm font-medium">Typography</span>
                    <span class="text-text-muted text-xs">Outfit + JetBrains Mono</span>
                  </div>
                  <span class="text-text-disabled text-xs italic"
                        style="font-family: 'Outfit', sans-serif;">
                    Aa Bb Cc
                  </span>
                </div>

              </div>
            </app-card>

            <div class="rounded-xl px-4 py-3 text-xs"
                 style="background: color-mix(in oklch, var(--color-accent) 6%, transparent);
                        border: 1px solid color-mix(in oklch, var(--color-accent) 12%, transparent);
                        color: var(--color-text-secondary);">
              <span class="material-symbols-rounded text-sm align-middle mr-1"
                    style="color: var(--color-accent);">info</span>
              Additional theme options will be available in a future update.
            </div>
          </div>
        }

      </div>
    </div>

    <style>
      .settings-field:focus {
        border-color: var(--color-accent) !important;
        box-shadow: 0 0 0 3px var(--color-accent-subtle), 0 0 0 1px var(--color-accent) !important;
        outline: none;
      }
    </style>
  `,
})
export class SettingsComponent implements OnInit {
  private readonly oauth       = inject(OAuthService);
  private readonly authService = inject(AuthService);
  private readonly fb      = inject(FormBuilder);
  readonly prefStore       = inject(NotificationPreferencesStore);
  readonly profileStore    = inject(ProfileStore);

  activeSection = signal<SettingsSection>('profile');
  isEditing     = signal(false);

  readonly sections = [
    { id: 'profile'       as SettingsSection, label: 'Profile',       icon: 'person' },
    { id: 'notifications' as SettingsSection, label: 'Notifications', icon: 'notifications' },
    { id: 'appearance'    as SettingsSection, label: 'Appearance',    icon: 'palette' },
  ];

  editForm = this.fb.group({
    firstName: [''],
    lastName:  [''],
    bio:       [''],
    avatarUrl: [''],
  });

  userEmail = signal(this.getClaimValue('email') ?? '');

  tokenExpiresIn = signal(this.computeTokenExpiry());

  constructor() {
    // Auto-close edit form when save completes successfully
    effect(() => {
      if (this.profileStore.saveSuccess()) {
        this.isEditing.set(false);
      }
    });
  }

  ngOnInit(): void {
    this.prefStore.loadPreferences();
    this.profileStore.loadProfile();
  }

  displayName(): string {
    const p = this.profileStore.profile();
    if (!p) return this.getClaimValue('preferred_username') ?? this.getClaimValue('email') ?? 'User';
    const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ');
    return fullName || p.email;
  }

  startEdit(): void {
    const p = this.profileStore.profile();
    this.editForm.patchValue({
      firstName: p?.firstName ?? '',
      lastName:  p?.lastName  ?? '',
      bio:       p?.bio       ?? '',
      avatarUrl: p?.avatarUrl ?? '',
    });
    this.isEditing.set(true);
  }

  cancelEdit(): void {
    this.isEditing.set(false);
    this.editForm.reset();
  }

  saveProfile(): void {
    if (this.editForm.invalid) return;

    const { firstName, lastName, bio, avatarUrl } = this.editForm.getRawValue();
    const profile = this.profileStore.profile();
    if (!profile) return;

    const req: UpdateProfileRequest = {
      version: profile.version,
    };

    if (firstName !== null && firstName !== undefined && firstName !== '') req.firstName = firstName;
    if (lastName  !== null && lastName  !== undefined && lastName  !== '') req.lastName  = lastName;
    if (bio       !== null && bio       !== undefined && bio       !== '') req.bio       = bio;
    if (avatarUrl !== null && avatarUrl !== undefined && avatarUrl !== '') req.avatarUrl = avatarUrl;

    this.profileStore.saveProfile(req);
  }

  private getClaimValue(key: string): string | null {
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    return claims?.[key] ?? null;
  }

  private computeTokenExpiry(): string {
    const exp = this.oauth.getAccessTokenExpiration();
    if (!exp) return 'unknown';
    const minutes = Math.round((exp - Date.now()) / 60000);
    if (minutes < 1) return 'less than a minute';
    return `${minutes} minute${minutes !== 1 ? 's' : ''}`;
  }

  eventLabel(eventType: string): string {
    return EVENT_LABELS[eventType] ?? eventType.toLowerCase().replace(/_/g, ' ');
  }

  channelVariant(channel: string): BadgeVariant {
    return CHANNEL_VARIANT[channel] ?? 'neutral';
  }

  onToggle(pref: NotificationPreference, event: MatSlideToggleChange): void {
    this.prefStore.updatePreference(
      pref.eventType as NotificationType,
      pref.channel   as NotificationChannel,
      event.checked,
    );
  }

  logout(): void {
    this.authService.logout();
  }
}
