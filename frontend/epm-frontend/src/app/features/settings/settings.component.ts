import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { MatSlideToggleModule, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { OAuthService } from 'angular-oauth2-oidc';
import { NotificationPreferencesStore } from '../notifications/store/notification-preferences.store';
import { NotificationPreference, NotificationChannel, NotificationType } from '../notifications/models/notification.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { CardComponent } from '../../shared/components/card/card.component';
import { BadgeComponent, BadgeVariant } from '../../shared/components/badge/badge.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';

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
    MatSlideToggleModule,
    PageHeaderComponent,
    CardComponent,
    BadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
    AvatarComponent,
  ],
  template: `
    <app-page-header
      title="Settings"
      description="Manage your account and application preferences"
    />

    <div class="flex gap-0 h-full">

      <!-- ── Sidebar de secciones ── -->
      <nav class="w-56 shrink-0 border-r p-4 flex flex-col gap-1"
           style="border-color: oklch(0.22 0.020 268 / 0.6); min-height: calc(100vh - 73px);">

        @for (section of sections; track section.id) {
          <button
            (click)="activeSection.set(section.id)"
            class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm
                   font-medium text-left w-full transition-all duration-150 cursor-pointer"
            [style.background]="activeSection() === section.id
              ? 'oklch(0.65 0.26 285 / 0.12)'
              : 'transparent'"
            [style.color]="activeSection() === section.id
              ? 'oklch(0.88 0.015 268)'
              : 'oklch(0.60 0.015 268)'"
          >
            <span class="material-symbols-rounded text-lg"
                  [style.color]="activeSection() === section.id
                    ? 'oklch(0.65 0.26 285)'
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

            <app-card>
              <div class="flex items-center gap-4">
                <app-avatar [name]="userName()" size="lg" />
                <div class="flex flex-col gap-0.5">
                  <span class="text-text-primary font-semibold text-sm">
                    {{ userName() }}
                  </span>
                  <span class="text-text-muted text-xs">{{ userEmail() }}</span>
                  <span class="text-text-disabled text-xs mt-1">
                    Managed by Keycloak — edit your profile in the identity provider
                  </span>
                </div>
              </div>
            </app-card>

            <app-card>
              <div class="flex flex-col gap-4">
                <h3 class="text-text-primary text-sm font-semibold">Session</h3>

                <div class="flex items-center justify-between py-2"
                     style="border-bottom: 1px solid oklch(0.22 0.020 268 / 0.5);">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-text-primary text-sm">Active session</span>
                    <span class="text-text-muted text-xs">
                      Token expires in ~{{ tokenExpiresIn() }}
                    </span>
                  </div>
                  <span class="size-2 rounded-full animate-glow-pulse"
                        style="background: oklch(0.74 0.18 152);
                               box-shadow: 0 0 6px oklch(0.74 0.18 152 / 0.6);">
                  </span>
                </div>

                <div class="flex justify-end pt-2">
                  <button (click)="logout()"
                          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm
                                 font-medium transition-all duration-150 cursor-pointer"
                          style="background: oklch(0.65 0.24 22 / 0.1);
                                 color: oklch(0.72 0.22 22);
                                 border: 1px solid oklch(0.65 0.24 22 / 0.25);"
                          onmouseover="this.style.background='oklch(0.65 0.24 22)';this.style.color='white'"
                          onmouseout="this.style.background='oklch(0.65 0.24 22 / 0.1)';this.style.color='oklch(0.72 0.22 22)'">
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
                       [style.border-color]="'oklch(0.22 0.020 268 / 0.5)'">

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
                        style="background: oklch(0.65 0.26 285 / 0.12);
                               color: oklch(0.65 0.26 285);
                               border: 1px solid oklch(0.65 0.26 285 / 0.2);">
                    Active
                  </span>
                </div>

                <div class="h-px" style="background: oklch(0.22 0.020 268 / 0.5);"></div>

                <!-- Accent color preview -->
                <div class="flex flex-col gap-3">
                  <span class="text-text-primary text-sm font-medium">Accent color</span>
                  <div class="flex items-center gap-3">
                    <div class="size-8 rounded-lg cursor-pointer ring-2 ring-offset-2"
                         style="background: linear-gradient(135deg, oklch(0.65 0.26 285), oklch(0.78 0.18 200));
                                ring-color: oklch(0.65 0.26 285);
                                ring-offset-color: oklch(0.07 0.02 268);">
                    </div>
                    <span class="text-text-muted text-xs">Violet → Cyan (default)</span>
                  </div>
                </div>

                <div class="h-px" style="background: oklch(0.22 0.020 268 / 0.5);"></div>

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
                 style="background: oklch(0.65 0.26 285 / 0.06);
                        border: 1px solid oklch(0.65 0.26 285 / 0.12);
                        color: oklch(0.55 0.015 268);">
              <span class="material-symbols-rounded text-sm align-middle mr-1"
                    style="color: oklch(0.65 0.26 285);">info</span>
              Additional theme options will be available in a future update.
            </div>
          </div>
        }

      </div>
    </div>
  `,
})
export class SettingsComponent implements OnInit {
  private readonly oauth   = inject(OAuthService);
  readonly prefStore       = inject(NotificationPreferencesStore);

  activeSection = signal<SettingsSection>('profile');

  readonly sections = [
    { id: 'profile'       as SettingsSection, label: 'Profile',       icon: 'person' },
    { id: 'notifications' as SettingsSection, label: 'Notifications', icon: 'notifications' },
    { id: 'appearance'    as SettingsSection, label: 'Appearance',    icon: 'palette' },
  ];

  ngOnInit(): void {
    this.prefStore.loadPreferences();
  }

  userName = signal(this.getClaimValue('preferred_username') ?? this.getClaimValue('email') ?? 'User');
  userEmail = signal(this.getClaimValue('email') ?? '');

  tokenExpiresIn = signal(this.computeTokenExpiry());

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
    this.oauth.logOut();
  }
}
