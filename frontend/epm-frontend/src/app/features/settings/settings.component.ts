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
import { ThemeService, Theme } from '../../core/theme/theme.service';
import { NotificationPreferencesStore } from '../notifications/store/notification-preferences.store';
import { ProfileStore } from './store/profile.store';
import { UpdateProfileRequest } from '../../core/models/user-profile.model';
import { NotificationPreference, NotificationChannel, NotificationType } from '../notifications/models/notification.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { CardComponent } from '../../shared/components/card/card.component';
import { BadgeVariant } from '../../shared/components/badge/badge.component';
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

type SettingsSection = 'profile' | 'notifications' | 'appearance' | 'workspace' | 'security';

interface AccentSwatch {
  id: string;
  label: string;
  color: string;
  isToken: boolean;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatSlideToggleModule,
    PageHeaderComponent,
    CardComponent,
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

      <!-- ── Sections sidebar ── -->
      <nav class="settings-nav w-56 shrink-0 border-r p-4 flex flex-col gap-1">

        @for (section of sections; track section.id) {
          <button
            (click)="activeSection.set(section.id)"
            [class.settings-nav-item--active]="activeSection() === section.id"
            class="settings-nav-item flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm
                   font-medium text-left w-full transition-all duration-150 cursor-pointer"
          >
            <span class="settings-nav-icon material-symbols-rounded text-lg">
              {{ section.icon }}
            </span>
            {{ section.label }}
          </button>
        }
      </nav>

      <!-- ── Active section content ── -->
      <div class="flex-1 p-6 max-w-2xl">

        <!-- PROFILE -->
        @if (activeSection() === 'profile') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="settings-section-title">Profile</h2>

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
                      <label class="settings-field-label text-sm font-semibold" for="firstName">
                        First name
                        <span class="settings-field-label--optional font-normal text-xs ml-1">optional</span>
                      </label>
                      <input
                        id="firstName" type="text" formControlName="firstName"
                        placeholder="Jane"
                        class="settings-field w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none"
                      />
                    </div>

                    <!-- Last name -->
                    <div class="flex flex-col gap-2">
                      <label class="settings-field-label text-sm font-semibold" for="lastName">
                        Last name
                        <span class="settings-field-label--optional font-normal text-xs ml-1">optional</span>
                      </label>
                      <input
                        id="lastName" type="text" formControlName="lastName"
                        placeholder="Doe"
                        class="settings-field w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none"
                      />
                    </div>
                  </div>

                  <!-- Bio -->
                  <div class="flex flex-col gap-2">
                      <label class="settings-field-label text-sm font-semibold" for="bio">
                        Bio
                        <span class="settings-field-label--optional font-normal text-xs ml-1">optional</span>
                      </label>
                      <textarea
                        id="bio" formControlName="bio" rows="3"
                        placeholder="Tell us a bit about yourself..."
                        maxlength="2000"
                        class="settings-field w-full px-4 py-3 rounded-xl text-sm resize-none transition-all duration-200 focus:outline-none"
                      ></textarea>
                  </div>

                  <!-- Avatar URL -->
                  <div class="flex flex-col gap-2">
                      <label class="settings-field-label text-sm font-semibold" for="avatarUrl">
                        Avatar URL
                        <span class="settings-field-label--optional font-normal text-xs ml-1">optional</span>
                      </label>
                      <input
                        id="avatarUrl" type="url" formControlName="avatarUrl"
                        placeholder="https://example.com/avatar.png"
                        class="settings-field w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none"
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
                          class="settings-logout-btn flex items-center gap-2 px-4 py-2 rounded-lg text-sm
                                 font-medium transition-all duration-150 cursor-pointer">
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
            <h2 class="settings-section-title">Notification preferences</h2>
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
                <!-- Column header row -->
                <div class="notif-table-header flex items-center px-6 py-3">
                  <span class="flex-1 text-xs font-semibold uppercase tracking-wider" style="color: var(--color-text-muted);">Event</span>
                  <div class="notif-col-header flex items-center gap-1.5">
                    <span class="material-symbols-rounded text-base">notifications</span>
                    <span class="text-xs font-semibold">In-app</span>
                  </div>
                  <div class="notif-col-header flex items-center gap-1.5 ml-8">
                    <span class="material-symbols-rounded text-base">mail</span>
                    <span class="text-xs font-semibold">Email</span>
                  </div>
                </div>

                <!-- Notification groups -->
                @for (group of notifGroups; track group.prefix) {
                  <!-- Group header with master toggles -->
                  <div class="notif-group-header flex items-center px-6 py-2.5">
                    <span class="flex-1 text-xs font-semibold uppercase tracking-wider">{{ group.label }}</span>
                    <mat-slide-toggle
                      [checked]="isGroupAllEnabled(group.prefix, 'IN_APP')"
                      (change)="toggleGroup(group.prefix, 'IN_APP', $event.checked)"
                      [attr.aria-label]="'Toggle all ' + group.label + ' in-app notifications'"
                      class="notif-col-toggle"
                    />
                    <mat-slide-toggle
                      [checked]="isGroupAllEnabled(group.prefix, 'EMAIL')"
                      (change)="toggleGroup(group.prefix, 'EMAIL', $event.checked)"
                      [attr.aria-label]="'Toggle all ' + group.label + ' email notifications'"
                      class="notif-col-toggle ml-8"
                    />
                  </div>

                  <!-- Event rows -->
                  @for (eventType of group.events; track eventType) {
                    <div class="notif-event-row flex items-center px-6 py-3">
                      <span class="flex-1 text-sm" style="color: var(--color-text-secondary);">{{ eventLabel(eventType) }}</span>
                      <mat-slide-toggle
                        [checked]="getPref(eventType, 'IN_APP')"
                        (change)="onToggleByType(eventType, 'IN_APP', $event)"
                        [attr.aria-label]="eventLabel(eventType) + ' in-app'"
                        class="notif-col-toggle"
                      />
                      <mat-slide-toggle
                        [checked]="getPref(eventType, 'EMAIL')"
                        (change)="onToggleByType(eventType, 'EMAIL', $event)"
                        [attr.aria-label]="eventLabel(eventType) + ' email'"
                        class="notif-col-toggle ml-8"
                      />
                    </div>
                  }
                }
              }
            </app-card>
          </div>
        }

        <!-- APPEARANCE -->
        @if (activeSection() === 'appearance') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="settings-section-title">Appearance</h2>

            <!-- Theme selector card -->
            <app-card>
              <div class="flex flex-col gap-5">
                <div class="flex flex-col gap-3">
                  <span class="text-text-primary text-sm font-semibold">Theme</span>
                  <div class="grid grid-cols-3 gap-3 sm:grid-cols-5">

                    <!-- Midnight -->
                    <button
                      (click)="setTheme('midnight')"
                      [class.theme-card--active]="themeService.theme() === 'midnight'"
                      class="theme-card flex flex-col items-center gap-2 p-4 rounded-xl border cursor-pointer transition-all duration-200"
                      aria-label="Midnight theme"
                    >
                      <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                           style="background: #0d0d1a; border: 1px solid #1a1a2e;">
                        <div class="h-1.5 rounded-full flex-1" style="background: #2a2a45;"></div>
                        <div class="h-1.5 rounded-full w-1/3" style="background: #8b5cf6;"></div>
                      </div>
                      <div class="flex items-center gap-1.5 w-full justify-between">
                        <span class="text-text-secondary text-xs font-medium">Midnight</span>
                        @if (themeService.theme() === 'midnight') {
                          <span class="material-symbols-rounded text-sm theme-check-icon">check_circle</span>
                        }
                      </div>
                    </button>

                    <!-- Amber -->
                    <button
                      (click)="setTheme('amber')"
                      [class.theme-card--active]="themeService.theme() === 'amber'"
                      class="theme-card flex flex-col items-center gap-2 p-4 rounded-xl border cursor-pointer transition-all duration-200"
                      aria-label="Amber theme"
                    >
                      <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                           style="background: #fdf6e3; border: 1px solid #e8d5b0;">
                        <div class="h-1.5 rounded-full flex-1" style="background: #e8d5b0;"></div>
                        <div class="h-1.5 rounded-full w-1/3" style="background: #d97706;"></div>
                      </div>
                      <div class="flex items-center gap-1.5 w-full justify-between">
                        <span class="text-text-secondary text-xs font-medium">Amber</span>
                        @if (themeService.theme() === 'amber') {
                          <span class="material-symbols-rounded text-sm theme-check-icon">check_circle</span>
                        }
                      </div>
                    </button>

                    <!-- Catppuccin -->
                    <button
                      (click)="setTheme('catppuccin')"
                      [class.theme-card--active]="themeService.theme() === 'catppuccin'"
                      class="theme-card flex flex-col items-center gap-2 p-4 rounded-xl border cursor-pointer transition-all duration-200"
                      aria-label="Catppuccin theme"
                    >
                      <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                           style="background: #1e1e2e; border: 1px solid #313244;">
                        <div class="h-1.5 rounded-full flex-1" style="background: #313244;"></div>
                        <div class="h-1.5 rounded-full w-1/3" style="background: #f5c2e7;"></div>
                      </div>
                      <div class="flex items-center gap-1.5 w-full justify-between">
                        <span class="text-text-secondary text-xs font-medium">Catppuccin</span>
                        @if (themeService.theme() === 'catppuccin') {
                          <span class="material-symbols-rounded text-sm theme-check-icon">check_circle</span>
                        }
                      </div>
                    </button>

                    <!-- Nord -->
                    <button
                      (click)="setTheme('nord')"
                      [class.theme-card--active]="themeService.theme() === 'nord'"
                      class="theme-card flex flex-col items-center gap-2 p-4 rounded-xl border cursor-pointer transition-all duration-200"
                      aria-label="Nord theme"
                    >
                      <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                           style="background: #2e3440; border: 1px solid #3b4252;">
                        <div class="h-1.5 rounded-full flex-1" style="background: #3b4252;"></div>
                        <div class="h-1.5 rounded-full w-1/3" style="background: #88c0d0;"></div>
                      </div>
                      <div class="flex items-center gap-1.5 w-full justify-between">
                        <span class="text-text-secondary text-xs font-medium">Nord</span>
                        @if (themeService.theme() === 'nord') {
                          <span class="material-symbols-rounded text-sm theme-check-icon">check_circle</span>
                        }
                      </div>
                    </button>

                    <!-- Rose -->
                    <button
                      (click)="setTheme('rose')"
                      [class.theme-card--active]="themeService.theme() === 'rose'"
                      class="theme-card flex flex-col items-center gap-2 p-4 rounded-xl border cursor-pointer transition-all duration-200"
                      aria-label="Rose theme"
                    >
                      <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                           style="background: #fff0f3; border: 1px solid #fce7f0;">
                        <div class="h-1.5 rounded-full flex-1" style="background: #fce7f0;"></div>
                        <div class="h-1.5 rounded-full w-1/3" style="background: #e11d75;"></div>
                      </div>
                      <div class="flex items-center gap-1.5 w-full justify-between">
                        <span class="text-text-secondary text-xs font-medium">Rose</span>
                        @if (themeService.theme() === 'rose') {
                          <span class="material-symbols-rounded text-sm theme-check-icon">check_circle</span>
                        }
                      </div>
                    </button>

                  </div>
                </div>

                <div class="settings-divider"></div>

                <!-- Accent color palette -->
                <div class="flex flex-col gap-3">
                  <span class="text-text-primary text-sm font-semibold">Accent color</span>
                  <div class="flex items-center gap-3">
                    @for (swatch of accentSwatches; track swatch.id) {
                      <button
                        (click)="selectAccent(swatch.id)"
                        [class.accent-swatch--selected]="selectedAccent() === swatch.id"
                        class="accent-swatch size-8 rounded-full cursor-pointer transition-all duration-200 border-2"
                        [attr.aria-label]="swatch.label"
                        [style.background]="swatch.color"
                      ></button>
                    }
                  </div>
                  <span class="text-text-muted text-xs">
                    Selected: {{ selectedAccentLabel() }}
                  </span>
                </div>

                <div class="settings-divider"></div>

                <!-- Compact mode -->
                <div class="flex items-center justify-between">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-text-primary text-sm font-medium">Compact mode</span>
                    <span class="text-text-muted text-xs">Reduce padding and spacing across the UI</span>
                  </div>
                  <mat-slide-toggle aria-label="Toggle compact mode" />
                </div>

                <!-- Reduce animations -->
                <div class="flex items-center justify-between">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-text-primary text-sm font-medium">Reduce animations</span>
                    <span class="text-text-muted text-xs">Minimize motion for accessibility or performance</span>
                  </div>
                  <mat-slide-toggle aria-label="Toggle reduce animations" />
                </div>

              </div>
            </app-card>
          </div>
        }

        <!-- WORKSPACE -->
        @if (activeSection() === 'workspace') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="settings-section-title">Workspace</h2>

            <!-- Language & Timezone -->
            <app-card>
              <div class="flex flex-col gap-5">

                <!-- Language -->
                <div class="flex flex-col gap-2">
                  <label class="settings-field-label text-sm font-semibold" for="ws-language">
                    Language
                  </label>
                  <div class="settings-select-wrapper">
                    <select id="ws-language" class="settings-select">
                      <option value="en" selected>English</option>
                      <option value="es">Spanish</option>
                      <option value="fr">French</option>
                      <option value="pt">Portuguese</option>
                    </select>
                    <span class="settings-select-chevron material-symbols-rounded">expand_more</span>
                  </div>
                </div>

                <div class="settings-divider"></div>

                <!-- Timezone -->
                <div class="flex flex-col gap-2">
                  <label class="settings-field-label text-sm font-semibold" for="ws-timezone">
                    Timezone
                  </label>
                  <div class="settings-select-wrapper">
                    <select id="ws-timezone" class="settings-select">
                      <option value="utc">UTC+0 — Universal Time</option>
                      <option value="america/argentina/buenos_aires" selected>UTC-3 — Buenos Aires</option>
                      <option value="america/new_york">UTC-5 — New York</option>
                      <option value="america/los_angeles">UTC-8 — Los Angeles</option>
                      <option value="europe/london">UTC+0 — London</option>
                      <option value="europe/paris">UTC+1 — Paris</option>
                      <option value="europe/berlin">UTC+1 — Berlin</option>
                      <option value="asia/shanghai">UTC+8 — Shanghai</option>
                      <option value="asia/tokyo">UTC+9 — Tokyo</option>
                      <option value="australia/sydney">UTC+10 — Sydney</option>
                    </select>
                    <span class="settings-select-chevron material-symbols-rounded">expand_more</span>
                  </div>
                </div>

              </div>
            </app-card>

            <!-- Date format -->
            <app-card>
              <div class="flex flex-col gap-3">
                <span class="text-text-primary text-sm font-semibold">Date format</span>
                <div class="grid grid-cols-3 gap-3">

                  <button
                    (click)="selectedDateFormat.set('MDY')"
                    [class.date-format-card--active]="selectedDateFormat() === 'MDY'"
                    class="date-format-card flex flex-col gap-1.5 p-4 rounded-xl border cursor-pointer transition-all duration-200 text-left"
                  >
                    <span class="text-text-primary text-xs font-semibold font-mono">MM/DD/YYYY</span>
                    <span class="text-text-muted text-xs">06/20/2026</span>
                  </button>

                  <button
                    (click)="selectedDateFormat.set('DMY')"
                    [class.date-format-card--active]="selectedDateFormat() === 'DMY'"
                    class="date-format-card flex flex-col gap-1.5 p-4 rounded-xl border cursor-pointer transition-all duration-200 text-left"
                  >
                    <span class="text-text-primary text-xs font-semibold font-mono">DD/MM/YYYY</span>
                    <span class="text-text-muted text-xs">20/06/2026</span>
                  </button>

                  <button
                    (click)="selectedDateFormat.set('ISO')"
                    [class.date-format-card--active]="selectedDateFormat() === 'ISO'"
                    class="date-format-card flex flex-col gap-1.5 p-4 rounded-xl border cursor-pointer transition-all duration-200 text-left"
                  >
                    <span class="text-text-primary text-xs font-semibold font-mono">YYYY-MM-DD</span>
                    <span class="text-text-muted text-xs">2026-06-20</span>
                  </button>

                </div>
              </div>
            </app-card>

            <!-- Start of week -->
            <app-card>
              <div class="flex flex-col gap-3">
                <span class="text-text-primary text-sm font-semibold">Start of week</span>
                <div class="flex gap-2">
                  <button
                    (click)="selectedStartOfWeek.set('sunday')"
                    [class.week-pill--active]="selectedStartOfWeek() === 'sunday'"
                    class="week-pill px-5 py-2 rounded-full text-sm font-medium cursor-pointer transition-all duration-200 border"
                  >
                    Sunday
                  </button>
                  <button
                    (click)="selectedStartOfWeek.set('monday')"
                    [class.week-pill--active]="selectedStartOfWeek() === 'monday'"
                    class="week-pill px-5 py-2 rounded-full text-sm font-medium cursor-pointer transition-all duration-200 border"
                  >
                    Monday
                  </button>
                </div>
              </div>
            </app-card>

            <!-- Info note -->
            <div class="rounded-xl px-4 py-3 text-xs flex items-start gap-2 settings-workspace-note">
              <span class="material-symbols-rounded text-sm shrink-0 mt-0.5">info</span>
              Workspace preferences are saved locally and apply to your browser session only.
            </div>
          </div>
        }

        <!-- SECURITY -->
        @if (activeSection() === 'security') {
          <div class="flex flex-col gap-4 animate-fade-up">
            <h2 class="settings-section-title">Security</h2>

            <!-- Active sessions -->
            <app-card>
              <div class="flex flex-col gap-4">
                <h3 class="text-text-primary text-sm font-semibold">Active sessions</h3>

                <!-- Session row 1 — current -->
                <div class="flex items-center gap-4 py-3"
                     style="border-bottom: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);">
                  <span class="material-symbols-rounded text-xl shrink-0" style="color: var(--color-text-muted);">computer</span>
                  <div class="flex flex-col gap-0.5 flex-1 min-w-0">
                    <span class="text-text-primary text-sm font-medium">Chrome on macOS</span>
                    <span class="text-text-muted text-xs">Buenos Aires, AR</span>
                  </div>
                  <div class="flex items-center gap-2 shrink-0">
                    <span class="settings-session-current-badge">Current session</span>
                    <span class="material-symbols-rounded text-base" style="color: var(--color-success);">lock</span>
                  </div>
                </div>

                <!-- Session row 2 — remote -->
                <div class="flex items-center gap-4 py-1">
                  <span class="material-symbols-rounded text-xl shrink-0" style="color: var(--color-text-muted);">smartphone</span>
                  <div class="flex flex-col gap-0.5 flex-1 min-w-0">
                    <span class="text-text-primary text-sm font-medium">Firefox on Windows</span>
                    <span class="text-text-muted text-xs">2 days ago</span>
                  </div>
                  <button class="settings-revoke-btn px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-all duration-150 shrink-0">
                    Revoke
                  </button>
                </div>

              </div>
            </app-card>

            <!-- Two-factor authentication -->
            <app-card>
              <div class="flex flex-col gap-4">
                <h3 class="text-text-primary text-sm font-semibold">Two-factor authentication</h3>

                <div class="flex items-start justify-between gap-4">
                  <div class="flex flex-col gap-1">
                    @if (!twoFactorEnabled()) {
                      <span class="text-text-primary text-sm font-medium">Enable 2FA for extra account security</span>
                      <span class="text-text-muted text-xs">Protect your account with an authenticator app.</span>
                    } @else {
                      <div class="flex items-center gap-2">
                        <span class="text-text-primary text-sm font-medium">Two-factor authentication</span>
                        <span class="settings-2fa-enabled-badge">Enabled</span>
                      </div>
                      <button class="settings-reconfigure-btn mt-2 px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-all duration-150 text-left w-fit">
                        Reconfigure
                      </button>
                    }
                  </div>
                  <mat-slide-toggle
                    [checked]="twoFactorEnabled()"
                    (change)="twoFactorEnabled.set($event.checked)"
                    aria-label="Toggle two-factor authentication"
                  />
                </div>
              </div>
            </app-card>

            <!-- Danger zone -->
            <app-card>
              <div class="flex flex-col gap-4">
                <h3 class="settings-danger-title text-sm font-semibold">Danger Zone</h3>

                <div class="flex items-center justify-between gap-4">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-text-primary text-sm font-medium">Delete account</span>
                    <span class="text-text-muted text-xs">
                      Permanently delete your account and all data. This cannot be undone.
                    </span>
                  </div>
                  <button
                    (click)="confirmDeleteAccount()"
                    class="settings-danger-btn px-4 py-2 rounded-lg text-sm font-medium cursor-pointer transition-all duration-150 shrink-0"
                  >
                    Delete account
                  </button>
                </div>
              </div>
            </app-card>

          </div>
        }

      </div>
    </div>

    <style>
      /* ─── Sidebar nav ─────────────────────────────────────────────────────── */
      .settings-nav {
        border-color: color-mix(in oklch, var(--color-border) 60%, transparent);
        min-height: calc(100vh - 73px);
      }

      .settings-nav-item {
        background: transparent;
        color: var(--color-text-secondary);
      }

      .settings-nav-item:hover {
        background: color-mix(in oklch, var(--color-accent) 8%, transparent);
        color: var(--color-text-primary);
      }

      .settings-nav-item--active {
        background: color-mix(in oklch, var(--color-accent) 12%, transparent) !important;
        color: var(--color-text-primary) !important;
      }

      .settings-nav-item--active .settings-nav-icon {
        color: var(--color-accent);
      }

      /* ─── Section titles ──────────────────────────────────────────────────── */
      .settings-section-title {
        color: var(--color-text-primary);
        font-family: 'Outfit', sans-serif;
        font-weight: 600;
        font-size: 1rem;
        line-height: 1.5rem;
      }

      /* ─── Form field labels ───────────────────────────────────────────────── */
      .settings-field-label {
        color: var(--color-text-secondary);
      }

      .settings-field-label--optional {
        color: var(--color-text-muted);
      }

      /* ─── Form inputs / textarea ──────────────────────────────────────────── */
      .settings-field {
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border);
        color: var(--color-text-primary);
        font-family: 'Outfit', sans-serif;
      }

      .settings-field::placeholder {
        color: var(--color-text-muted);
      }

      .settings-field:focus {
        border-color: var(--color-accent) !important;
        box-shadow: 0 0 0 3px var(--color-accent-subtle), 0 0 0 1px var(--color-accent) !important;
        outline: none;
      }

      /* ─── Logout button — pure CSS hover, no JS ───────────────────────────── */
      .settings-logout-btn {
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
        color: var(--color-danger);
        border: 1px solid color-mix(in oklch, var(--color-danger) 25%, transparent);
      }

      .settings-logout-btn:hover {
        background: var(--color-danger);
        color: oklch(1 0 0);
        border-color: var(--color-danger);
      }

      /* ─── Divider ─────────────────────────────────────────────────────────── */
      .settings-divider {
        height: 1px;
        background: color-mix(in oklch, var(--color-border) 50%, transparent);
      }

      /* ─── Appearance: Theme cards ─────────────────────────────────────────── */
      .theme-card {
        background: var(--color-bg-surface);
        border-color: color-mix(in oklch, var(--color-border) 60%, transparent);
      }

      .theme-card:hover {
        border-color: color-mix(in oklch, var(--color-accent) 40%, transparent);
        background: color-mix(in oklch, var(--color-accent) 4%, transparent);
      }

      .theme-card--active {
        border-color: var(--color-accent) !important;
        background: color-mix(in oklch, var(--color-accent) 8%, transparent) !important;
      }

      .theme-check-icon {
        color: var(--color-accent);
      }

      /* ─── Appearance: Accent swatches ────────────────────────────────────── */
      .accent-swatch {
        border-color: transparent;
        outline: 2px solid transparent;
        outline-offset: 2px;
      }

      .accent-swatch:hover {
        outline-color: color-mix(in oklch, var(--color-accent) 60%, transparent);
        transform: scale(1.1);
      }

      .accent-swatch--selected {
        outline-color: var(--color-accent) !important;
        transform: scale(1.1);
        border-color: transparent !important;
      }

      /* ─── Workspace: Custom select ────────────────────────────────────────── */
      .settings-select-wrapper {
        position: relative;
        display: flex;
        align-items: center;
      }

      .settings-select {
        appearance: none;
        -webkit-appearance: none;
        width: 100%;
        padding: 0.75rem 2.5rem 0.75rem 1rem;
        border-radius: 0.75rem;
        border: 1px solid var(--color-border);
        background: var(--color-bg-surface);
        color: var(--color-text-primary);
        font-family: 'Outfit', sans-serif;
        font-size: 0.875rem;
        cursor: pointer;
        transition: border-color 200ms, box-shadow 200ms;
      }

      .settings-select:focus {
        border-color: var(--color-accent);
        box-shadow: 0 0 0 3px var(--color-accent-subtle), 0 0 0 1px var(--color-accent);
        outline: none;
      }

      .settings-select-chevron {
        position: absolute;
        right: 0.75rem;
        font-size: 1.125rem;
        color: var(--color-text-muted);
        pointer-events: none;
      }

      /* ─── Workspace: Date format cards ───────────────────────────────────── */
      .date-format-card {
        background: var(--color-bg-surface);
        border-color: color-mix(in oklch, var(--color-border) 60%, transparent);
      }

      .date-format-card:hover {
        border-color: color-mix(in oklch, var(--color-accent) 40%, transparent);
        background: color-mix(in oklch, var(--color-accent) 4%, transparent);
      }

      .date-format-card--active {
        border-color: var(--color-accent) !important;
        background: color-mix(in oklch, var(--color-accent) 8%, transparent) !important;
      }

      /* ─── Workspace: Week pills ───────────────────────────────────────────── */
      .week-pill {
        background: transparent;
        border-color: color-mix(in oklch, var(--color-border) 60%, transparent);
        color: var(--color-text-secondary);
      }

      .week-pill:hover {
        border-color: color-mix(in oklch, var(--color-accent) 40%, transparent);
        color: var(--color-text-primary);
        background: color-mix(in oklch, var(--color-accent) 6%, transparent);
      }

      .week-pill--active {
        border-color: var(--color-accent) !important;
        color: var(--color-accent) !important;
        background: color-mix(in oklch, var(--color-accent) 10%, transparent) !important;
      }

      /* ─── Workspace: Info note ────────────────────────────────────────────── */
      .settings-workspace-note {
        background: color-mix(in oklch, var(--color-accent) 6%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-accent) 12%, transparent);
        color: var(--color-text-secondary);
      }

      .settings-workspace-note .material-symbols-rounded {
        color: var(--color-accent);
      }

      /* ─── Security: Session badges ────────────────────────────────────────── */
      .settings-session-current-badge {
        display: inline-flex;
        align-items: center;
        padding: 0.125rem 0.5rem;
        border-radius: 9999px;
        font-size: 0.625rem;
        font-weight: 600;
        letter-spacing: 0.025em;
        background: color-mix(in oklch, var(--color-success) 12%, transparent);
        color: var(--color-success);
        border: 1px solid color-mix(in oklch, var(--color-success) 25%, transparent);
      }

      /* ─── Security: Revoke button ─────────────────────────────────────────── */
      .settings-revoke-btn {
        background: transparent;
        color: var(--color-danger);
        border: 1px solid color-mix(in oklch, var(--color-danger) 30%, transparent);
      }

      .settings-revoke-btn:hover {
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
        border-color: var(--color-danger);
      }

      /* ─── Security: 2FA enabled badge ────────────────────────────────────── */
      .settings-2fa-enabled-badge {
        display: inline-flex;
        align-items: center;
        padding: 0.125rem 0.5rem;
        border-radius: 9999px;
        font-size: 0.625rem;
        font-weight: 600;
        background: color-mix(in oklch, var(--color-success) 12%, transparent);
        color: var(--color-success);
        border: 1px solid color-mix(in oklch, var(--color-success) 25%, transparent);
      }

      /* ─── Security: Reconfigure button ───────────────────────────────────── */
      .settings-reconfigure-btn {
        background: color-mix(in oklch, var(--color-accent) 10%, transparent);
        color: var(--color-accent);
        border: 1px solid color-mix(in oklch, var(--color-accent) 25%, transparent);
      }

      .settings-reconfigure-btn:hover {
        background: color-mix(in oklch, var(--color-accent) 18%, transparent);
        border-color: var(--color-accent);
      }

      /* ─── Security: Danger zone ───────────────────────────────────────────── */
      .settings-danger-title {
        color: var(--color-danger);
      }

      .settings-danger-btn {
        background: transparent;
        color: var(--color-danger);
        border: 1px solid color-mix(in oklch, var(--color-danger) 40%, transparent);
      }

      .settings-danger-btn:hover {
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
        border-color: var(--color-danger);
      }

      /* ─── Notification table ──────────────────────────────────────────────── */
      .notif-table-header {
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 60%, transparent);
        color: var(--color-text-muted);
      }

      .notif-col-header {
        width: 4rem;
        justify-content: center;
        color: var(--color-text-muted);
      }

      .notif-group-header {
        background: color-mix(in oklch, var(--color-accent) 5%, transparent);
        border-top: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 40%, transparent);
        color: var(--color-text-secondary);
        letter-spacing: 0.05em;
      }

      .notif-event-row {
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 30%, transparent);
      }

      .notif-event-row:last-child {
        border-bottom: none;
      }

      .notif-col-toggle {
        width: 4rem;
        display: flex;
        justify-content: center;
      }
    </style>
  `,
})
export class SettingsComponent implements OnInit {
  private readonly oauth       = inject(OAuthService);
  private readonly authService = inject(AuthService);
  private readonly fb          = inject(FormBuilder);
  readonly themeService        = inject(ThemeService);
  readonly prefStore           = inject(NotificationPreferencesStore);
  readonly profileStore        = inject(ProfileStore);

  activeSection = signal<SettingsSection>('profile');
  isEditing     = signal(false);

  // Appearance signals
  selectedAccent = signal<string>('violet');

  // Workspace signals
  selectedDateFormat  = signal<'MDY' | 'DMY' | 'ISO'>('DMY');
  selectedStartOfWeek = signal<'sunday' | 'monday'>('monday');

  // Security signals
  twoFactorEnabled = signal(false);

  readonly sections = [
    { id: 'profile'       as SettingsSection, label: 'Profile',       icon: 'person' },
    { id: 'notifications' as SettingsSection, label: 'Notifications', icon: 'notifications' },
    { id: 'appearance'    as SettingsSection, label: 'Appearance',    icon: 'palette' },
    { id: 'workspace'     as SettingsSection, label: 'Workspace',     icon: 'tune' },
    { id: 'security'      as SettingsSection, label: 'Security',      icon: 'shield' },
  ];

  readonly accentSwatches: AccentSwatch[] = [
    { id: 'violet',   label: 'Violet (default)',  color: 'var(--color-accent)',  isToken: true },
    { id: 'cyan',     label: 'Cyan',              color: 'var(--color-cyan)',    isToken: true },
    { id: 'fuchsia',  label: 'Fuchsia',           color: '#e879f9',              isToken: false },
    { id: 'amber',    label: 'Amber',             color: '#f59e0b',              isToken: false },
    { id: 'emerald',  label: 'Emerald',           color: '#22c55e',              isToken: false },
    { id: 'rose',     label: 'Rose',              color: '#ef4444',              isToken: false },
  ];

  readonly notifGroups: Array<{
    label: string;
    prefix: string;
    events: NotificationType[];
  }> = [
    {
      label: 'Tasks',
      prefix: 'TASK',
      events: ['TASK_CREATED', 'TASK_ASSIGNED', 'TASK_STATUS_CHANGED', 'TASK_DELETED'],
    },
    {
      label: 'Projects',
      prefix: 'PROJECT',
      events: ['PROJECT_CREATED', 'PROJECT_ARCHIVED', 'TEAM_ASSIGNED_TO_PROJECT'],
    },
    {
      label: 'Team',
      prefix: 'MEMBER',
      events: ['MEMBER_JOINED_TEAM', 'MEMBER_LEFT_TEAM'],
    },
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

  // ─── Appearance methods ────────────────────────────────────────────────────

  setTheme(theme: Theme): void {
    this.themeService.setTheme(theme);
  }

  selectAccent(id: string): void {
    this.selectedAccent.set(id);
  }

  selectedAccentLabel(): string {
    return this.accentSwatches.find(s => s.id === this.selectedAccent())?.label ?? '';
  }

  // ─── Security methods ──────────────────────────────────────────────────────

  confirmDeleteAccount(): void {
    window.confirm('Are you sure you want to permanently delete your account? This action cannot be undone.');
  }

  // ─── Shared helpers ────────────────────────────────────────────────────────

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

  /** Look up a single preference's enabled state */
  getPref(eventType: NotificationType, channel: NotificationChannel): boolean {
    return this.prefStore.preferences().find(
      p => p.eventType === eventType && p.channel === channel
    )?.enabled ?? false;
  }

  /** Check if ALL events in a group+channel are enabled */
  isGroupAllEnabled(prefix: string, channel: NotificationChannel): boolean {
    const group = this.notifGroups.find(g => g.prefix === prefix);
    if (!group) return false;
    return group.events.every(eventType => this.getPref(eventType as NotificationType, channel));
  }

  /** Toggle all events in a group+channel */
  toggleGroup(prefix: string, channel: NotificationChannel, enabled: boolean): void {
    const group = this.notifGroups.find(g => g.prefix === prefix);
    if (!group) return;
    group.events.forEach(eventType => {
      const pref = this.prefStore.preferences().find(
        p => p.eventType === eventType && p.channel === channel
      );
      if (pref) {
        this.prefStore.updatePreference(eventType as NotificationType, channel, enabled);
      }
    });
  }

  /** Used in event rows — delegates to existing onToggle but by type */
  onToggleByType(eventType: NotificationType, channel: NotificationChannel, event: MatSlideToggleChange): void {
    const pref = this.prefStore.preferences().find(
      p => p.eventType === eventType && p.channel === channel
    );
    if (pref) this.onToggle(pref, event);
  }

  logout(): void {
    this.authService.logout();
  }
}
