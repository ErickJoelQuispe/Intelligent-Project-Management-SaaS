import { Component, inject, OnInit } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { MatDividerModule } from '@angular/material/divider';
import { NotificationPreferencesStore } from '../../store/notification-preferences.store';
import { NotificationPreference, NotificationChannel, NotificationType } from '../../models/notification.model';

@Component({
  selector: 'app-notification-preferences',
  standalone: true,
  imports: [
    MatCardModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatDividerModule,
  ],
  template: `
    <mat-card class="preferences-card">
      <mat-card-header>
        <mat-card-title>Notification Preferences</mat-card-title>
        <mat-card-subtitle>Manage how you receive notifications</mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        @if (store.loading()) {
          <div class="loading-container" data-testid="loading-spinner">
            <mat-spinner diameter="40" />
          </div>
        } @else {
          @for (pref of store.preferences(); track pref.eventType + pref.channel) {
            <div
              class="pref-row"
              [attr.data-testid]="'pref-row-' + pref.eventType + '-' + pref.channel"
            >
              <div class="pref-info">
                <span class="pref-event-type">{{ pref.eventType }}</span>
                <span class="pref-channel">{{ pref.channel }}</span>
              </div>
              <mat-slide-toggle
                [checked]="pref.enabled"
                [attr.data-testid]="'pref-toggle-' + pref.eventType + '-' + pref.channel"
                (change)="onToggleChange(pref, $event)"
                [attr.aria-label]="pref.eventType + ' ' + pref.channel"
              />
            </div>
            <mat-divider />
          }

          @if (store.preferences().length === 0) {
            <p class="empty-state" data-testid="no-preferences">No preferences configured.</p>
          }
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .preferences-card {
      max-width: 600px;
      margin: 24px auto;
    }

    .loading-container {
      display: flex;
      justify-content: center;
      padding: 32px;
    }

    .pref-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 0;
    }

    .pref-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .pref-event-type {
      font-weight: 500;
      font-size: 0.9rem;
    }

    .pref-channel {
      font-size: 0.75rem;
      color: rgba(0,0,0,0.54);
    }

    .empty-state {
      text-align: center;
      color: rgba(0,0,0,0.54);
      padding: 24px;
    }
  `],
})
export class NotificationPreferencesComponent implements OnInit {
  readonly store = inject(NotificationPreferencesStore);

  ngOnInit(): void {
    this.store.loadPreferences();
  }

  onToggleChange(pref: NotificationPreference, event: MatSlideToggleChange): void {
    this.store.updatePreference(
      pref.eventType as NotificationType,
      pref.channel as NotificationChannel,
      event.checked,
    );
  }
}
