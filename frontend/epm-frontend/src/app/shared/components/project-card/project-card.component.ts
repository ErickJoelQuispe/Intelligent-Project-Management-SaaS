import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProjectStatusBadgeComponent } from '../project-status-badge/project-status-badge.component';
import { Project, ProjectStatus } from '../../../core/models/project.model';

/** Deterministic hue from project name — same name always gets same color */
function nameToHue(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  }
  // Avoid the 30-60° range (too close to warning/amber in our palette)
  const raw = hash % 320;
  return raw < 30 ? raw + 90 : raw + 60;
}

@Component({
  selector: 'app-project-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, ProjectStatusBadgeComponent, TranslocoPipe],
  host: { style: 'display: flex; flex-direction: column; height: 100%;' },
  template: `
    <article
      class="project-card animate-card-in"
      [class.project-card--archived]="isArchived()"
      [routerLink]="['/projects', project().id]"
      [style.border-left-color]="accentColor()"
    >
      <!-- Hover glow backdrop -->
      <div
        class="project-card-glow"
        [style.background]="hoverGlow()"
        aria-hidden="true"
      ></div>

      <!-- Archive / Restore button — visible on hover, top-right -->
      @if (isArchived()) {
        <button
          class="project-card-archive-btn project-card-archive-btn--restore"
          (click)="onRestore($event)"
          title="Restore project"
          aria-label="Restore {{ project().name }}"
        >
          <span class="material-symbols-rounded" aria-hidden="true">settings_backup_restore</span>
        </button>
      } @else {
        <button
          class="project-card-archive-btn"
          (click)="onArchive($event)"
          title="Archive project"
          aria-label="Archive {{ project().name }}"
        >
          <span class="material-symbols-rounded" aria-hidden="true">archive</span>
        </button>
      }

      <!-- ── Header ─────────────────────────────────── -->
      <header class="project-card-header">

        <!-- Color dot + title -->
        <div class="project-card-title-row">
          <span
            class="project-color-dot"
            [style.background]="accentColor()"
            [style.box-shadow]="accentShadow()"
            aria-hidden="true"
          ></span>
          <h3 class="project-card-title">{{ project().name }}</h3>
        </div>

        <!-- Status + visibility -->
        <div class="project-card-meta-row">
          <app-project-status-badge [status]="project().status" />
          <span class="project-visibility-chip" [attr.aria-label]="'Visibility: ' + project().visibility">
            <span class="material-symbols-rounded" aria-hidden="true">
              {{ visibilityIcon() }}
            </span>
            {{ visibilityLabel() }}
          </span>
        </div>

      </header>

      <!-- ── Divider ────────────────────────────────── -->
      <div class="project-card-divider" aria-hidden="true"></div>

      <!-- ── Body ──────────────────────────────────── -->
      <div class="project-card-body">

        <!-- Description -->
        <p class="project-card-desc">
          {{ project().description || ('projects.list.noDescription' | transloco) }}
        </p>

        <!-- Created date -->
        <div class="project-card-date" aria-label="Created on {{ project().createdAt | date: 'MMMM d, yyyy' }}">
          <span class="material-symbols-rounded" aria-hidden="true">calendar_today</span>
          <span>{{ project().createdAt | date: 'MMM d, yyyy' }}</span>
        </div>

      </div>

    </article>

    <style>
      /* ── Card shell ──────────────────────────────── */
      .project-card {
        position: relative;
        display: flex;
        flex-direction: column;
        height: 100%;           /* fills the listitem flex container */
        border-radius: 0.875rem;
        overflow: hidden;
        background: var(--glass-bg);
        backdrop-filter: blur(12px);
        -webkit-backdrop-filter: blur(12px);
        border: 1px solid var(--color-border);
        border-left-width: 3px;
        transition:
          border-color 0.2s ease,
          box-shadow 0.2s ease,
          transform 0.18s cubic-bezier(0.2, 0, 0, 1);
        cursor: pointer;
        text-decoration: none;
        color: inherit;
      }
      .project-card:hover {
        border-color: var(--color-border-strong);
        box-shadow: var(--shadow-lg);
        transform: translateY(-2px);
      }
      .project-card--archived {
        opacity: 0.6;
        filter: saturate(0.4);
      }

      /* ── Hover overlay ────────────────────────────── */
      .project-card::after {
        content: '';
        position: absolute;
        inset: 0;
        background: color-mix(in oklch, var(--color-text-primary) 3%, transparent);
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.2s ease;
        z-index: 0;
      }
      .project-card:hover::after {
        opacity: 1;
      }

      /* ── Hover glow backdrop ─────────────────────── */
      .project-card-glow {
        position: absolute;
        top: -40px;
        left: 50%;
        transform: translateX(-50%);
        width: 70%;
        height: 80px;
        filter: blur(20px);
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.3s ease;
        z-index: 0;
      }
      .project-card:hover .project-card-glow {
        opacity: 1;
      }

      /* ── Archive button (hover-reveal, top-right) ── */
      .project-card-archive-btn {
        position: absolute;
        top: 0.625rem;
        right: 0.625rem;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.375rem;
        border: none;
        background: color-mix(in oklch, var(--color-bg-overlay) 70%, transparent);
        color: var(--color-text-muted);
        cursor: pointer;
        opacity: 0;
        transition: opacity 0.15s ease, background 0.15s ease, color 0.15s ease;
        padding: 0;
      }
      .project-card:hover .project-card-archive-btn {
        opacity: 1;
      }
      .project-card-archive-btn:hover {
        background: color-mix(in oklch, var(--color-danger) 12%, var(--color-bg-overlay));
        color: var(--color-danger);
      }
      .project-card-archive-btn:focus-visible {
        outline: 2px solid var(--color-danger);
        outline-offset: 1px;
        opacity: 1;
      }
      .project-card-archive-btn--restore:hover {
        background: color-mix(in oklch, var(--color-accent) 12%, var(--color-bg-overlay));
        color: var(--color-accent);
      }
      .project-card-archive-btn--restore:focus-visible {
        outline: 2px solid var(--color-accent);
      }
      .project-card-archive-btn .material-symbols-rounded {
        font-size: 1rem;
      }

      /* ── Header ──────────────────────────────────── */
      .project-card-header {
        position: relative;
        z-index: 1;
        display: flex;
        flex-direction: column;
        gap: 0.625rem;
        padding: 1.125rem 1.25rem 0.875rem;
      }

      .project-card-title-row {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        min-width: 0;
        padding-right: 1.5rem; /* room for archive btn */
      }

      .project-color-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        flex-shrink: 0;
        transition: transform 0.2s ease;
      }
      .project-card:hover .project-color-dot {
        transform: scale(1.35);
      }

      .project-card-title {
        font-family: 'Outfit', sans-serif;
        font-size: 0.9375rem;
        font-weight: 650;
        line-height: 1.3;
        color: var(--color-text-primary);
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        letter-spacing: -0.01em;
      }

      .project-card-meta-row {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        flex-wrap: wrap;
      }

      .project-visibility-chip {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.1875rem 0.5rem;
        border-radius: 9999px;
        font-size: 0.6875rem;
        font-weight: 500;
        letter-spacing: 0.02em;
        color: var(--color-text-muted);
        background: color-mix(in oklch, var(--color-bg-overlay) 60%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
      }
      .project-visibility-chip .material-symbols-rounded {
        font-size: 0.75rem;
      }

      /* ── Divider ─────────────────────────────────── */
      .project-card-divider {
        height: 1px;
        margin: 0 1.25rem;
        background: color-mix(in oklch, var(--color-border) 50%, transparent);
        flex-shrink: 0;
      }

      /* ── Body ────────────────────────────────────── */
      .project-card-body {
        position: relative;
        z-index: 1;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        padding: 0.875rem 1.25rem 1rem;
        flex: 1;
      }

      .project-card-desc {
        flex: 1;               /* pushes the date to the bottom */
        font-size: 0.8125rem;
        line-height: 1.6;
        color: var(--color-text-secondary);
        margin: 0;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .project-card-date {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.75rem;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
        font-feature-settings: 'tnum';
      }
      .project-card-date .material-symbols-rounded {
        font-size: 0.875rem;
      }
    </style>
  `,
})
export class ProjectCardComponent {
  project          = input.required<Project>();
  projectArchived  = output<Project>();
  projectRestored  = output<Project>();

  isArchived = computed(() => this.project().status === ProjectStatus.ARCHIVED);

  /** Deterministic hue derived from project name */
  private readonly hue = computed(() => nameToHue(this.project().name));

  accentColor   = computed(() => `oklch(0.68 0.20 ${this.hue()})`);
  accentGradient = computed(() =>
    `linear-gradient(90deg, oklch(0.62 0.22 ${this.hue()}) 0%, oklch(0.72 0.18 ${this.hue() + 30}) 100%)`
  );
  accentShadow  = computed(() => `0 0 8px oklch(0.68 0.20 ${this.hue()} / 0.6)`);
  hoverGlow     = computed(() =>
    `radial-gradient(ellipse, oklch(0.68 0.20 ${this.hue()} / 0.12) 0%, transparent 70%)`
  );

  visibilityIcon = computed(() => {
    switch (this.project().visibility) {
      case 'PUBLIC':  return 'public';
      case 'TEAM':    return 'group';
      default:        return 'lock';
    }
  });

  visibilityLabel = computed(() => {
    switch (this.project().visibility) {
      case 'PUBLIC':  return 'Public';
      case 'TEAM':    return 'Team';
      default:        return 'Private';
    }
  });

  onArchive(event: MouseEvent): void {
    event.stopPropagation();
    this.projectArchived.emit(this.project());
  }

  onRestore(event: MouseEvent): void {
    event.stopPropagation();
    this.projectRestored.emit(this.project());
  }
}
