import {
  Component,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  OnInit,
  inject,
  signal,
  computed,
  effect,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppPreferencesService } from '../../core/services/app-preferences.service';
import { AuthApiService } from '../../core/services/auth-api.service';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService, Theme } from '../../core/theme/theme.service';
import { NotificationPreferencesStore } from '../notifications/store/notification-preferences.store';
import { ProfileStore } from './store/profile.store';
import { SessionsStore } from './store/sessions.store';
import { UserService } from './services/user.service';
import { ConfirmDeleteAccountDialogComponent } from './components/confirm-delete-account-dialog/confirm-delete-account-dialog.component';
import { DEFAULT_PREFERENCES, UpdateProfileRequest, UserPreferences } from '../../core/models/user-profile.model';
import { NotificationPreference, NotificationChannel, NotificationType } from '../notifications/models/notification.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { BadgeVariant } from '../../shared/components/badge/badge.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { ButtonComponent } from '../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner.component';

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
    DatePipe,
    TranslocoPipe,
    PageHeaderComponent,
    SpinnerComponent,
    EmptyStateComponent,
    AvatarComponent,
    ButtonComponent,
    ErrorBannerComponent,
  ],
  template: `
    <app-page-header
      [title]="'settings.title' | transloco"
      [description]="'settings.description' | transloco"
    />

    <div class="flex gap-0 h-full">

      <!-- ── Sections sidebar ── -->
      <nav class="settings-nav w-56 shrink-0 border-r p-4 flex flex-col gap-1">

        @for (section of sections; track section.id) {
          <button
            (click)="onSectionChange(section.id)"
            [class.settings-nav-item--active]="activeSection() === section.id"
            class="settings-nav-item flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm
                   font-medium text-left w-full transition-all duration-150 cursor-pointer"
          >
            <span class="settings-nav-icon material-symbols-rounded text-lg">
              {{ section.icon }}
            </span>
            {{ section.label | transloco }}
          </button>
        }
      </nav>

      <!-- ── Active section content ── -->
      <div class="flex-1 p-6 max-w-2xl">

        <!-- PROFILE -->
        @if (activeSection() === 'profile') {
          <div class="flex flex-col gap-6 animate-fade-up">
            <h2 class="settings-section-title">{{ 'settings.profile.title' | transloco }}</h2>

            <!-- Profile info -->
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
                  <p class="text-text-disabled text-xs italic">{{ 'settings.profile.noBio' | transloco }}</p>
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
                    {{ 'settings.profile.editBtn' | transloco }}
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
                      {{ 'settings.profile.firstName' | transloco }}
                      <span class="settings-field-label--optional font-normal text-xs ml-1">{{ 'common.optional' | transloco }}</span>
                    </label>
                    <input
                      id="firstName" type="text" formControlName="firstName"
                      [placeholder]="'settings.profile.firstNamePlaceholder' | transloco"
                      class="settings-field w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none"
                    />
                  </div>

                  <!-- Last name -->
                  <div class="flex flex-col gap-2">
                    <label class="settings-field-label text-sm font-semibold" for="lastName">
                      {{ 'settings.profile.lastName' | transloco }}
                      <span class="settings-field-label--optional font-normal text-xs ml-1">{{ 'common.optional' | transloco }}</span>
                    </label>
                    <input
                      id="lastName" type="text" formControlName="lastName"
                      [placeholder]="'settings.profile.lastNamePlaceholder' | transloco"
                      class="settings-field w-full px-4 py-3 rounded-xl text-sm transition-all duration-200 focus:outline-none"
                    />
                  </div>
                </div>

                <!-- Bio -->
                <div class="flex flex-col gap-2">
                    <label class="settings-field-label text-sm font-semibold" for="bio">
                      {{ 'settings.profile.bio' | transloco }}
                      <span class="settings-field-label--optional font-normal text-xs ml-1">{{ 'common.optional' | transloco }}</span>
                    </label>
                    <textarea
                      id="bio" formControlName="bio" rows="3"
                      [placeholder]="'settings.profile.bioPlaceholder' | transloco"
                      maxlength="2000"
                      class="settings-field w-full px-4 py-3 rounded-xl text-sm resize-none transition-all duration-200 focus:outline-none"
                    ></textarea>
                </div>

                <!-- Avatar URL -->
                <div class="flex flex-col gap-2">
                    <label class="settings-field-label text-sm font-semibold" for="avatarUrl">
                      {{ 'settings.profile.avatarUrl' | transloco }}
                      <span class="settings-field-label--optional font-normal text-xs ml-1">{{ 'common.optional' | transloco }}</span>
                    </label>
                    <input
                      id="avatarUrl" type="url" formControlName="avatarUrl"
                      [placeholder]="'settings.profile.avatarUrlPlaceholder' | transloco"
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
                    {{ 'settings.profile.cancelBtn' | transloco }}
                  </app-button>
                  <app-button
                    type="submit"
                    variant="primary"
                    size="sm"
                    [loading]="profileStore.saving()"
                    [disabled]="profileStore.saving()"
                  >
                    {{ 'settings.profile.saveBtn' | transloco }}
                  </app-button>
                </div>
              </form>
            }

            <div class="settings-divider"></div>

            <!-- Session -->
            <div class="flex flex-col gap-4">
              <h3 class="text-text-secondary text-xs font-semibold uppercase tracking-wider">{{ 'settings.profile.session' | transloco }}</h3>

              <div class="flex items-center justify-between py-3 settings-row-border">
                <div class="flex flex-col gap-0.5">
                  <span class="text-text-primary text-sm">{{ 'settings.profile.activeSession' | transloco }}</span>
                  <span class="text-text-muted text-xs">
                    {{ 'settings.profile.tokenExpiry' | transloco }}{{ tokenExpiresIn() }}
                  </span>
                </div>
                <span class="size-2 rounded-full animate-glow-pulse"
                      style="background: var(--color-success);
                             box-shadow: 0 0 6px color-mix(in oklch, var(--color-success) 60%, transparent);">
                </span>
              </div>

              <div class="flex justify-end">
                <button (click)="logout()"
                        class="settings-logout-btn flex items-center gap-2 px-4 py-2 rounded-lg text-sm
                               font-medium transition-all duration-150 cursor-pointer">
                   <span class="material-symbols-rounded text-sm">logout</span>
                   {{ 'common.signOut' | transloco }}
                </button>
              </div>
            </div>
          </div>
        }

        <!-- NOTIFICATIONS -->
        @if (activeSection() === 'notifications') {
          <div class="flex flex-col gap-6 animate-fade-up">
            <div>
              <h2 class="settings-section-title">{{ 'settings.notifications.title' | transloco }}</h2>
              <p class="text-text-muted text-sm mt-1">
                {{ 'settings.notifications.description' | transloco }}
              </p>
            </div>

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
              <div class="notif-table-header flex items-center py-2">
                <span class="flex-1 text-xs font-semibold uppercase tracking-wider" style="color: var(--color-text-muted);">{{ 'settings.notifications.eventCol' | transloco }}</span>
                <div class="notif-col-header flex items-center gap-1.5">
                  <span class="material-symbols-rounded text-base">notifications</span>
                  <span class="text-xs font-semibold">{{ 'settings.notifications.inAppCol' | transloco }}</span>
                </div>
                <div class="notif-col-header flex items-center gap-1.5 ml-8">
                  <span class="material-symbols-rounded text-base">mail</span>
                  <span class="text-xs font-semibold">{{ 'settings.notifications.emailCol' | transloco }}</span>
                </div>
              </div>

              <!-- Notification groups -->
              @for (group of notifGroups; track group.prefix) {
                <!-- Group header with master toggles -->
                <div class="notif-group-header flex items-center py-2">
                  <span class="flex-1 text-xs font-semibold uppercase tracking-wider">{{ group.label | transloco }}</span>
                  <div class="notif-col-toggle">
                    <button
                      role="switch"
                      [attr.aria-checked]="isGroupAllEnabled(group.prefix, 'IN_APP')"
                      [attr.aria-label]="'settings.notifications.toggleAllInApp' | transloco : { label: group.label | transloco }"
                      [class.notif-toggle--on]="isGroupAllEnabled(group.prefix, 'IN_APP')"
                      class="notif-toggle"
                      (click)="toggleGroup(group.prefix, 'IN_APP', !isGroupAllEnabled(group.prefix, 'IN_APP'))"
                    >
                      <span class="notif-toggle-thumb"></span>
                    </button>
                  </div>
                  <div class="notif-col-toggle ml-8">
                    <button
                      role="switch"
                      [attr.aria-checked]="isGroupAllEnabled(group.prefix, 'EMAIL')"
                      [attr.aria-label]="'settings.notifications.toggleAllEmail' | transloco : { label: group.label | transloco }"
                      [class.notif-toggle--on]="isGroupAllEnabled(group.prefix, 'EMAIL')"
                      class="notif-toggle"
                      (click)="toggleGroup(group.prefix, 'EMAIL', !isGroupAllEnabled(group.prefix, 'EMAIL'))"
                    >
                      <span class="notif-toggle-thumb"></span>
                    </button>
                  </div>
                </div>

                <!-- Event rows -->
                @for (eventType of group.events; track eventType) {
                  <div class="notif-event-row flex items-center py-3">
                    <span class="flex-1 text-sm" style="color: var(--color-text-secondary);">{{ eventLabel(eventType) }}</span>
                    <div class="notif-col-toggle">
                      <button
                        role="switch"
                        [attr.aria-checked]="getPref(eventType, 'IN_APP')"
                        [attr.aria-label]="eventLabel(eventType) + ' in-app'"
                        [class.notif-toggle--on]="getPref(eventType, 'IN_APP')"
                        class="notif-toggle"
                        (click)="onToggleByType(eventType, 'IN_APP', { checked: !getPref(eventType, 'IN_APP') })"
                      >
                        <span class="notif-toggle-thumb"></span>
                      </button>
                    </div>
                    <div class="notif-col-toggle ml-8">
                      <button
                        role="switch"
                        [attr.aria-checked]="getPref(eventType, 'EMAIL')"
                        [attr.aria-label]="eventLabel(eventType) + ' email'"
                        [class.notif-toggle--on]="getPref(eventType, 'EMAIL')"
                        class="notif-toggle"
                        (click)="onToggleByType(eventType, 'EMAIL', { checked: !getPref(eventType, 'EMAIL') })"
                      >
                        <span class="notif-toggle-thumb"></span>
                      </button>
                    </div>
                  </div>
                }
              }
            }
          </div>
        }

        <!-- APPEARANCE -->
        @if (activeSection() === 'appearance') {
          <div class="flex flex-col gap-6 animate-fade-up">
            <h2 class="settings-section-title">{{ 'settings.appearance.title' | transloco }}</h2>

            <!-- Theme selector -->
            <div class="flex flex-col gap-3">
              <span class="text-text-secondary text-xs font-semibold uppercase tracking-wider">{{ 'settings.appearance.theme' | transloco }}</span>
              <div class="grid grid-cols-3 gap-3 sm:grid-cols-5">

                <!-- Midnight -->
                <button
                  (click)="setTheme('midnight')"
                  [class.theme-card--active]="themeService.theme() === 'midnight'"
                  class="theme-card flex flex-col items-center gap-2 p-4 rounded-xl border cursor-pointer transition-all duration-200"
                  [attr.aria-label]="'settings.appearance.themes.midnight' | transloco"
                 >
                   <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                        style="background: #0d0d1a; border: 1px solid #1a1a2e;">
                     <div class="h-1.5 rounded-full flex-1" style="background: #2a2a45;"></div>
                     <div class="h-1.5 rounded-full w-1/3" style="background: #8b5cf6;"></div>
                   </div>
                   <div class="flex items-center gap-1.5 w-full justify-between">
                     <span class="text-text-secondary text-xs font-medium">{{ 'settings.appearance.themes.midnight' | transloco }}</span>
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
                  [attr.aria-label]="'settings.appearance.themes.amber' | transloco"
                 >
                   <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                        style="background: #fdf6e3; border: 1px solid #e8d5b0;">
                     <div class="h-1.5 rounded-full flex-1" style="background: #e8d5b0;"></div>
                     <div class="h-1.5 rounded-full w-1/3" style="background: #d97706;"></div>
                   </div>
                   <div class="flex items-center gap-1.5 w-full justify-between">
                     <span class="text-text-secondary text-xs font-medium">{{ 'settings.appearance.themes.amber' | transloco }}</span>
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
                  [attr.aria-label]="'settings.appearance.themes.catppuccin' | transloco"
                 >
                   <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                        style="background: #1e1e2e; border: 1px solid #313244;">
                     <div class="h-1.5 rounded-full flex-1" style="background: #313244;"></div>
                     <div class="h-1.5 rounded-full w-1/3" style="background: #f5c2e7;"></div>
                   </div>
                   <div class="flex items-center gap-1.5 w-full justify-between">
                     <span class="text-text-secondary text-xs font-medium">{{ 'settings.appearance.themes.catppuccin' | transloco }}</span>
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
                  [attr.aria-label]="'settings.appearance.themes.nord' | transloco"
                 >
                   <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                        style="background: #2e3440; border: 1px solid #3b4252;">
                     <div class="h-1.5 rounded-full flex-1" style="background: #3b4252;"></div>
                     <div class="h-1.5 rounded-full w-1/3" style="background: #88c0d0;"></div>
                   </div>
                   <div class="flex items-center gap-1.5 w-full justify-between">
                     <span class="text-text-secondary text-xs font-medium">{{ 'settings.appearance.themes.nord' | transloco }}</span>
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
                  [attr.aria-label]="'settings.appearance.themes.rose' | transloco"
                 >
                   <div class="rounded-lg w-full h-12 flex items-end p-1.5 gap-1"
                        style="background: #fff0f3; border: 1px solid #fce7f0;">
                     <div class="h-1.5 rounded-full flex-1" style="background: #fce7f0;"></div>
                     <div class="h-1.5 rounded-full w-1/3" style="background: #e11d75;"></div>
                   </div>
                   <div class="flex items-center gap-1.5 w-full justify-between">
                     <span class="text-text-secondary text-xs font-medium">{{ 'settings.appearance.themes.rose' | transloco }}</span>
                    @if (themeService.theme() === 'rose') {
                      <span class="material-symbols-rounded text-sm theme-check-icon">check_circle</span>
                    }
                  </div>
                </button>

              </div>
            </div>

            <div class="settings-divider"></div>

            <!-- Compact mode -->
            <div class="flex items-center justify-between py-1">
              <div class="flex flex-col gap-0.5">
                <span class="text-text-primary text-sm font-medium">{{ 'settings.appearance.compactMode' | transloco }}</span>
                <span class="text-text-muted text-xs">{{ 'settings.appearance.compactModeDesc' | transloco }}</span>
              </div>
              <button
                role="switch"
                [attr.aria-checked]="compactMode()"
                [attr.aria-label]="'settings.appearance.toggleCompactMode' | transloco"
                [class.notif-toggle--on]="compactMode()"
                class="notif-toggle"
                (click)="toggleCompactMode()"
              >
                <span class="notif-toggle-thumb"></span>
              </button>
            </div>

            <!-- Reduce animations -->
            <div class="flex items-center justify-between py-1">
              <div class="flex flex-col gap-0.5">
                <span class="text-text-primary text-sm font-medium">{{ 'settings.appearance.reduceAnimations' | transloco }}</span>
                <span class="text-text-muted text-xs">{{ 'settings.appearance.reduceAnimationsDesc' | transloco }}</span>
              </div>
              <button
                role="switch"
                [attr.aria-checked]="reduceAnimations()"
                [attr.aria-label]="'settings.appearance.toggleReduceAnimations' | transloco"
                [class.notif-toggle--on]="reduceAnimations()"
                class="notif-toggle"
                (click)="toggleReduceAnimations()"
              >
                <span class="notif-toggle-thumb"></span>
              </button>
            </div>
          </div>
        }

        <!-- WORKSPACE -->
        @if (activeSection() === 'workspace') {
          <div class="flex flex-col gap-6 animate-fade-up">
            <h2 class="settings-section-title">{{ 'settings.workspace.title' | transloco }}</h2>

            <!-- Language -->
            <div class="flex flex-col gap-2">
              <label class="settings-field-label text-sm font-semibold" for="ws-language">
                 {{ 'settings.workspace.language' | transloco }}
               </label>
              <div class="settings-select-wrapper">
                <select
                  id="ws-language"
                  class="settings-select"
                  [value]="selectedLanguage()"
                  (change)="selectedLanguage.set($any($event.target).value)"
                >
                   <option value="en">{{ 'settings.workspace.languages.en' | transloco }}</option>
                   <option value="es">{{ 'settings.workspace.languages.es' | transloco }}</option>
                   <option value="pt">{{ 'settings.workspace.languages.pt' | transloco }}</option>
                </select>
                <span class="settings-select-chevron material-symbols-rounded">expand_more</span>
              </div>
            </div>

            <div class="settings-divider"></div>

            <!-- Timezone -->
            <div class="flex flex-col gap-2">
              <label class="settings-field-label text-sm font-semibold" for="ws-timezone">
                 {{ 'settings.workspace.timezone' | transloco }}
               </label>
              <div class="settings-select-wrapper">
                <select
                  id="ws-timezone"
                  class="settings-select"
                  [value]="selectedTimezone()"
                  (change)="selectedTimezone.set($any($event.target).value)"
                >
                  <option value="UTC">UTC+0 — Universal Time</option>
                  <option value="America/Argentina/Buenos_Aires">UTC-3 — Buenos Aires</option>
                  <option value="America/New_York">UTC-5 — New York</option>
                  <option value="America/Los_Angeles">UTC-8 — Los Angeles</option>
                  <option value="Europe/London">UTC+0 — London</option>
                  <option value="Europe/Paris">UTC+1 — Paris</option>
                  <option value="Europe/Berlin">UTC+1 — Berlin</option>
                  <option value="Asia/Shanghai">UTC+8 — Shanghai</option>
                  <option value="Asia/Tokyo">UTC+9 — Tokyo</option>
                  <option value="Australia/Sydney">UTC+10 — Sydney</option>
                </select>
                <span class="settings-select-chevron material-symbols-rounded">expand_more</span>
              </div>
            </div>

            <div class="settings-divider"></div>

            <!-- Date format -->
            <div class="flex flex-col gap-3">
              <span class="settings-field-label text-sm font-semibold">{{ 'settings.workspace.dateFormat' | transloco }}</span>
              <div class="grid grid-cols-3 gap-3">

                <button
                  (click)="selectedDateFormat.set('MM/DD/YYYY')"
                  [class.date-format-card--active]="selectedDateFormat() === 'MM/DD/YYYY'"
                  class="date-format-card flex flex-col gap-1.5 p-4 rounded-xl border cursor-pointer transition-all duration-200 text-left"
                >
                  <span class="text-text-primary text-xs font-semibold font-mono">MM/DD/YYYY</span>
                  <span class="text-text-muted text-xs">06/20/2026</span>
                </button>

                <button
                  (click)="selectedDateFormat.set('DD/MM/YYYY')"
                  [class.date-format-card--active]="selectedDateFormat() === 'DD/MM/YYYY'"
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

            <div class="settings-divider"></div>

            <!-- Start of week -->
            <div class="flex flex-col gap-3">
              <span class="settings-field-label text-sm font-semibold">{{ 'settings.workspace.startOfWeek' | transloco }}</span>
              <div class="flex gap-2">
                 <button
                   (click)="selectedStartOfWeek.set('SUNDAY')"
                   [class.week-pill--active]="selectedStartOfWeek() === 'SUNDAY'"
                   class="week-pill px-5 py-2 rounded-full text-sm font-medium cursor-pointer transition-all duration-200 border"
                 >
                   {{ 'settings.workspace.days.sunday' | transloco }}
                 </button>
                 <button
                   (click)="selectedStartOfWeek.set('MONDAY')"
                   [class.week-pill--active]="selectedStartOfWeek() === 'MONDAY'"
                   class="week-pill px-5 py-2 rounded-full text-sm font-medium cursor-pointer transition-all duration-200 border"
                 >
                   {{ 'settings.workspace.days.monday' | transloco }}
                 </button>
                 <button
                   (click)="selectedStartOfWeek.set('SATURDAY')"
                   [class.week-pill--active]="selectedStartOfWeek() === 'SATURDAY'"
                   class="week-pill px-5 py-2 rounded-full text-sm font-medium cursor-pointer transition-all duration-200 border"
                 >
                   {{ 'settings.workspace.days.saturday' | transloco }}
                 </button>
              </div>
            </div>

            <div class="flex justify-end">
              <app-button
                variant="primary"
                size="sm"
                [loading]="profileStore.saving()"
                [disabled]="profileStore.saving()"
                (click)="saveWorkspacePreferences()"
              >
                {{ 'settings.workspace.saveBtn' | transloco }}
              </app-button>
            </div>

            @if (profileStore.error()) {
              <app-error-banner [message]="profileStore.error()!" variant="inline" />
            }

            <!-- Info note -->
            <div class="px-4 py-3 text-xs flex items-start gap-2 settings-workspace-note rounded-lg">
              <span class="material-symbols-rounded text-sm shrink-0 mt-0.5">info</span>
              {{ 'settings.workspace.syncNote' | transloco }}
            </div>
          </div>
        }

        <!-- SECURITY -->
        @if (activeSection() === 'security') {
          <div class="flex flex-col gap-6 animate-fade-up">
            <h2 class="settings-section-title">{{ 'settings.security.title' | transloco }}</h2>

            <!-- Active sessions -->
            <div class="flex flex-col gap-1">
              <h3 class="text-text-secondary text-xs font-semibold uppercase tracking-wider mb-3">{{ 'settings.security.activeSessions' | transloco }}</h3>

              @if (sessionsStore.isLoading()) {
                <app-spinner size="md" [full]="true" />
              } @else if (sessionsStore.sessions().length === 0) {
                 <p class="text-text-muted text-sm">{{ 'settings.security.noActiveSessions' | transloco }}</p>
              } @else {
                @for (session of sessionsStore.sessions(); track session.sessionId) {
                  <div class="flex items-center gap-4 py-3 settings-row-border">
                    <div class="flex flex-col gap-0.5 flex-1 min-w-0">
                      <span class="text-text-primary text-sm font-medium">{{ session.ipAddress }}</span>
                       <span class="text-text-muted text-xs">
                         {{ 'settings.security.started' | transloco }} {{ session.started | date:'medium' }}
                       </span>
                       <span class="text-text-muted text-xs">
                         {{ 'settings.security.lastSeen' | transloco }} {{ session.lastAccess | date:'medium' }}
                       </span>
                    </div>
                    <div class="flex items-center gap-2 shrink-0">
                      @if (session.sessionId === sessionsStore.currentSessionId()) {
                         <span class="settings-session-current-badge">{{ 'settings.security.currentSession' | transloco }}</span>
                      }
                      <button
                        class="settings-revoke-btn px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-all duration-150"
                        [disabled]="sessionsStore.revokingSessionId() === session.sessionId"
                         (click)="revokeSession(session.sessionId)"
                       >
                         {{ 'settings.security.revokeBtn' | transloco }}
                      </button>
                    </div>
                  </div>
                }
              }

              @if (sessionsStore.error()) {
                <p class="text-xs mt-2" style="color: var(--color-danger);">
                  {{ sessionsStore.error() }}
                </p>
              }
            </div>

            <div class="settings-divider"></div>

            <!-- Danger zone -->
            <div class="flex flex-col gap-4">
              <h3 class="settings-danger-title text-xs font-semibold uppercase tracking-wider">{{ 'settings.security.dangerZone' | transloco }}</h3>

              <div class="flex items-center justify-between gap-4">
                <div class="flex flex-col gap-0.5">
                   <span class="text-text-primary text-sm font-medium">{{ 'settings.security.deleteAccount' | transloco }}</span>
                   <span class="text-text-muted text-xs">
                     {{ 'settings.security.deleteAccountDesc' | transloco }}
                   </span>
                </div>
                <button
                  (click)="confirmDeleteAccount()"
                  [disabled]="isDeletingAccount()"
                  class="settings-danger-btn px-4 py-2 rounded-lg text-sm font-medium cursor-pointer transition-all duration-150 shrink-0"
                >
                   @if (isDeletingAccount()) {
                     {{ 'settings.security.deletingAccount' | transloco }}
                   } @else {
                     {{ 'settings.security.deleteAccount' | transloco }}
                   }
                </button>
              </div>

              @if (deleteError()) {
                <app-error-banner [message]="deleteError()!" variant="inline" />
              }
            </div>

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

      /* ─── Toggle pill (notif + appearance) ───────────────────────────────── */
      .notif-toggle {
        position: relative;
        display: inline-flex;
        align-items: center;
        width: 2.25rem;
        height: 1.25rem;
        border-radius: 9999px;
        border: 1.5px solid var(--color-border-strong);
        cursor: pointer;
        padding: 0;
        flex-shrink: 0;
        background: transparent;
        transition: background 200ms ease, border-color 200ms ease;
      }

      .notif-toggle:focus-visible {
        outline: 2px solid var(--color-accent);
        outline-offset: 2px;
      }

      .notif-toggle--on {
        background: var(--color-accent) !important;
        border-color: var(--color-accent) !important;
      }

      .notif-toggle-thumb {
        position: absolute;
        left: 0.175rem;
        width: 0.8rem;
        height: 0.8rem;
        border-radius: 9999px;
        background: var(--color-text-muted);
        box-shadow: 0 1px 2px oklch(0 0 0 / 0.15);
        transition: transform 200ms ease, background 200ms ease;
        pointer-events: none;
      }

      .notif-toggle--on .notif-toggle-thumb {
        transform: translateX(1rem);
        background: oklch(1 0 0 / 0.95);
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

      /* ─── Row separator (sessions, session active row) ───────────────────── */
      .settings-row-border {
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
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
  private readonly oauth                = inject(OAuthService);
  private readonly authService          = inject(AuthService);
  private readonly authApiService       = inject(AuthApiService);
  private readonly userService          = inject(UserService);
  private readonly dialog               = inject(MatDialog);
  private readonly fb                   = inject(FormBuilder);
  private readonly translocoService     = inject(TranslocoService);
  readonly themeService                 = inject(ThemeService);
  readonly prefStore                    = inject(NotificationPreferencesStore);
  readonly profileStore                 = inject(ProfileStore);
  readonly sessionsStore                = inject(SessionsStore);
  readonly appPreferencesService        = inject(AppPreferencesService);
  private readonly cdr                  = inject(ChangeDetectorRef);

  activeSection = signal<SettingsSection>('profile');
  isEditing     = signal(false);

  // Delete account signals
  isDeletingAccount = signal(false);
  deleteError       = signal<string | null>(null);

  // Appearance signals
  selectedAccent  = signal<string>('violet');
  compactMode     = computed(() => this.appPreferencesService.compactMode());
  reduceAnimations = computed(() => this.appPreferencesService.reduceAnimations());

  // Workspace signals — initialised from profile preferences on load
  selectedLanguage    = signal<string>(DEFAULT_PREFERENCES.language);
  selectedTimezone    = signal<string>(DEFAULT_PREFERENCES.timezone);
  selectedDateFormat  = signal<string>(DEFAULT_PREFERENCES.dateFormat);
  selectedStartOfWeek = signal<string>(DEFAULT_PREFERENCES.startOfWeek);

  readonly sections = [
    { id: 'profile'       as SettingsSection, label: 'settings.nav.profile',       icon: 'person' },
    { id: 'notifications' as SettingsSection, label: 'settings.nav.notifications', icon: 'notifications' },
    { id: 'appearance'    as SettingsSection, label: 'settings.nav.appearance',    icon: 'palette' },
    { id: 'workspace'     as SettingsSection, label: 'settings.nav.workspace',     icon: 'tune' },
    { id: 'security'      as SettingsSection, label: 'settings.nav.security',      icon: 'shield' },
  ];

  readonly accentSwatches: AccentSwatch[] = [
    { id: 'violet',   label: 'settings.appearance.accents.violet',   color: 'var(--color-accent)',  isToken: true },
    { id: 'cyan',     label: 'settings.appearance.accents.cyan',     color: 'var(--color-cyan)',    isToken: true },
    { id: 'fuchsia',  label: 'settings.appearance.accents.fuchsia',  color: '#e879f9',              isToken: false },
    { id: 'amber',    label: 'settings.appearance.accents.amber',    color: '#f59e0b',              isToken: false },
    { id: 'emerald',  label: 'settings.appearance.accents.emerald',  color: '#22c55e',              isToken: false },
    { id: 'rose',     label: 'settings.appearance.accents.rose',     color: '#ef4444',              isToken: false },
  ];

  readonly notifGroups: Array<{
    label: string;
    prefix: string;
    events: NotificationType[];
  }> = [
    {
      label: 'settings.notifications.groups.tasks',
      prefix: 'TASK',
      events: ['TASK_CREATED', 'TASK_ASSIGNED', 'TASK_STATUS_CHANGED', 'TASK_DELETED'],
    },
    {
      label: 'settings.notifications.groups.projects',
      prefix: 'PROJECT',
      events: ['PROJECT_CREATED', 'PROJECT_ARCHIVED', 'TEAM_ASSIGNED_TO_PROJECT'],
    },
    {
      label: 'settings.notifications.groups.team',
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

    // Populate workspace preference signals when profile loads
    effect(() => {
      const profile = this.profileStore.profile();
      if (profile?.preferences) {
        this.selectedLanguage.set(profile.preferences.language);
        this.selectedTimezone.set(profile.preferences.timezone);
        this.selectedDateFormat.set(profile.preferences.dateFormat);
        this.selectedStartOfWeek.set(profile.preferences.startOfWeek);
      }
    });
  }

  ngOnInit(): void {
    this.prefStore.loadPreferences();
    this.profileStore.loadProfile();
  }

  // ─── Navigation ───────────────────────────────────────────────────────────

  onSectionChange(section: SettingsSection): void {
    this.activeSection.set(section);
    if (section === 'security') {
      this.sessionsStore.loadSessions();
    }
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

  // ─── Workspace methods ─────────────────────────────────────────────────────

  saveWorkspacePreferences(): void {
    const profile = this.profileStore.profile();
    if (!profile) return;

    const preferences: UserPreferences = {
      language:    this.selectedLanguage()    || DEFAULT_PREFERENCES.language,
      timezone:    this.selectedTimezone()    || DEFAULT_PREFERENCES.timezone,
      dateFormat:  this.selectedDateFormat()  || DEFAULT_PREFERENCES.dateFormat,
      startOfWeek: this.selectedStartOfWeek() || DEFAULT_PREFERENCES.startOfWeek,
    };

    const req: UpdateProfileRequest = {
      version: profile.version,
      preferences,
    };

    this.profileStore.saveProfile(req);

    // Task 3.3: hot-swap language immediately without page reload
    this.translocoService.setActiveLang(this.selectedLanguage());
    localStorage.setItem('app.language', this.selectedLanguage());
  }

  // ─── Appearance methods ────────────────────────────────────────────────────

  setTheme(theme: Theme): void {
    this.themeService.setTheme(theme);
  }

  toggleCompactMode(): void {
    this.appPreferencesService.setCompactMode(!this.appPreferencesService.compactMode());
    this.cdr.markForCheck();
  }

  toggleReduceAnimations(): void {
    this.appPreferencesService.setReduceAnimations(!this.appPreferencesService.reduceAnimations());
    this.cdr.markForCheck();
  }

  selectAccent(id: string): void {
    this.selectedAccent.set(id);
  }

  selectedAccentLabel(): string {
    const key = this.accentSwatches.find(s => s.id === this.selectedAccent())?.label;
    return key ? this.translocoService.translate(key) : '';
  }

  // ─── Security methods ──────────────────────────────────────────────────────

  revokeSession(sessionId: string): void {
    const isCurrent = sessionId === this.sessionsStore.currentSessionId();
    void this.sessionsStore.revokeSession(sessionId, isCurrent);
  }

  async confirmDeleteAccount(): Promise<void> {
    const dialogRef = this.dialog.open(ConfirmDeleteAccountDialogComponent);
    const confirmed = await firstValueFrom(dialogRef.afterClosed());
    if (!confirmed) return;

    // Step 1: disable Keycloak user
    this.isDeletingAccount.set(true);
    this.deleteError.set(null);
    try {
      await firstValueFrom(this.authApiService.disableAccount());
    } catch {
      this.deleteError.set('Could not deactivate account. Please try again later.');
      this.isDeletingAccount.set(false);
      return;
    }

    // Step 2: soft-delete profile in user-service
    try {
      await firstValueFrom(this.userService.deleteOwnProfile());
    } catch {
      this.deleteError.set('Account deactivated but profile deletion failed. Contact support.');
      this.isDeletingAccount.set(false);
      return;
    }

    // Step 3: logout
    this.authService.logout();
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
    return this.translocoService.translate(`notifications.events.${eventType}`);
  }

  channelVariant(channel: string): BadgeVariant {
    return CHANNEL_VARIANT[channel] ?? 'neutral';
  }

  onToggle(pref: NotificationPreference, event: { checked: boolean }): void {
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
  onToggleByType(eventType: NotificationType, channel: NotificationChannel, event: { checked: boolean }): void {
    const pref = this.prefStore.preferences().find(
      p => p.eventType === eventType && p.channel === channel
    );
    if (pref) this.onToggle(pref, event);
  }

  logout(): void {
    this.authService.logout();
  }
}
