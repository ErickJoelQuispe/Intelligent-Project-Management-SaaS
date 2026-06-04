import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { OAuthService } from 'angular-oauth2-oidc';
import { NotificationBellComponent } from '../../../features/notifications/components/notification-bell/notification-bell.component';
import { NotificationStore } from '../../../features/notifications/store/notification.store';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, MatToolbarModule, MatButtonModule, MatIconModule, NotificationBellComponent],
  template: `
    <mat-toolbar color="primary" class="navbar">
      <span class="brand">EPM</span>
      <nav class="nav-links">
        <a mat-button routerLink="/projects">Projects</a>
      </nav>
      <span class="spacer"></span>
      <app-notification-bell />
      <button mat-icon-button (click)="logout()" aria-label="Logout">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
  `,
  styles: [`
    .navbar {
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .brand {
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      margin-right: 24px;
    }

    .nav-links {
      display: flex;
      gap: 4px;
    }

    .spacer {
      flex: 1;
    }
  `],
})
export class NavbarComponent implements OnInit, OnDestroy {
  private readonly oauthService = inject(OAuthService);
  private readonly store = inject(NotificationStore);

  ngOnInit(): void {
    this.store.connectSse();
  }

  ngOnDestroy(): void {
    this.store.disconnectSse();
  }

  logout(): void {
    this.oauthService.logOut();
  }
}
