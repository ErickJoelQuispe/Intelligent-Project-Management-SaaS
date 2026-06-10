import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
} from '@angular/core';
import { MatSlideToggleModule, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { NotificationPreferencesStore } from '../../store/notification-preferences.store';
import { NotificationPreference, NotificationChannel, NotificationType } from '../../models/notification.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { CardComponent } from '../../../../shared/components/card/card.component';
import { BadgeComponent } from '../../../../shared/components/badge/badge.component';
import { BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';

// Mapa de eventType → label legible
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

@Component({
  selector: 'app-notification-preferences',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatSlideToggleModule,
    PageHeaderComponent,
    CardComponent,
    BadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
  ],
  template: `
    <app-page-header
      title="Notification preferences"
      description="Choose how and when you want to be notified"
    />

    <div class="p-6 max-w-2xl">
      <app-card [noPadding]="true">

        @if (store.loading()) {
          <app-spinner size="md" [full]="true" data-testid="loading-spinner" />

        } @else if (store.preferences().length === 0) {
          <app-empty-state
            icon="notifications_off"
            title="No preferences configured"
            size="sm"
            data-testid="no-preferences"
          />

        } @else {
          @for (pref of store.preferences(); track pref.eventType + pref.channel; let last = $last) {
            <div
              class="flex items-center justify-between px-6 py-4"
              [class.border-b]="!last"
              [class.border-border]="!last"
              [attr.data-testid]="'pref-row-' + pref.eventType + '-' + pref.channel"
            >
              <!-- Event info -->
              <div class="flex flex-col gap-1.5">
                <span class="text-text-primary text-sm font-medium">
                  {{ eventLabel(pref.eventType) }}
                </span>
                <app-badge
                  [variant]="channelVariant(pref.channel)"
                  size="sm"
                >
                  {{ pref.channel === 'IN_APP' ? 'In-app' : 'Email' }}
                </app-badge>
              </div>

              <!-- Toggle — mantenemos Material solo para este control -->
              <mat-slide-toggle
                [checked]="pref.enabled"
                [attr.data-testid]="'pref-toggle-' + pref.eventType + '-' + pref.channel"
                [attr.aria-label]="eventLabel(pref.eventType) + ' via ' + pref.channel"
                (change)="onToggle(pref, $event)"
              />
            </div>
          }
        }

      </app-card>
    </div>
  `,
})
export class NotificationPreferencesComponent implements OnInit {
  readonly store = inject(NotificationPreferencesStore);

  ngOnInit(): void {
    this.store.loadPreferences();
  }

  eventLabel(eventType: string): string {
    return EVENT_LABELS[eventType] ?? eventType.toLowerCase().replace(/_/g, ' ');
  }

  channelVariant(channel: string): BadgeVariant {
    return CHANNEL_VARIANT[channel] ?? 'neutral';
  }

  onToggle(pref: NotificationPreference, event: MatSlideToggleChange): void {
    this.store.updatePreference(
      pref.eventType as NotificationType,
      pref.channel   as NotificationChannel,
      event.checked,
    );
  }
}
