import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { ProjectService } from '../project.service';
import { AiService, TaskDraft } from '../../ai/ai.service';
import { TaskService } from '../../tasks/task.service';
import { TeamService } from '../../teams/team.service';
import { Project, ProjectStatus, ProjectTeamAssignment } from '../../../core/models/project.model';
import { Team } from '../../../core/models/team.model';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { ProjectStatusBadgeComponent } from '../../../shared/components/project-status-badge/project-status-badge.component';
import { AiDraftTaskItemComponent } from '../../../shared/components/ai-draft-task-item/ai-draft-task-item.component';
import { AiChatComponent } from '../../ai/chat/ai-chat.component';
import { TaskPanelComponent } from '../../tasks/task-panel/task-panel.component';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';

type Visibility = 'PRIVATE' | 'TEAM' | 'PUBLIC';

/** Deterministic hue from project name — same name always gets same color */
function nameToHue(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  }
  const raw = hash % 320;
  return raw < 30 ? raw + 90 : raw + 60;
}

@Component({
  selector: 'app-project-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    FormsModule,
    DatePipe,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
    ProjectStatusBadgeComponent,
    AiDraftTaskItemComponent,
    AiChatComponent,
    TaskPanelComponent,
  ],
  template: `
    @if (loading()) {
      <div class="pdp-loading">
        <app-spinner size="lg" [full]="true" label="Loading project..." />
      </div>

    } @else if (error()) {
      <div class="pdp-error">
        <app-error-banner [message]="error()!" />
      </div>

    } @else if (project(); as p) {

      <div class="pdp-shell">

        <!-- ══ LEFT PANEL ══════════════════════════════════ -->
        <aside class="pdp-left" aria-label="Project overview">

          <!-- Back link + archive/restore action -->
          <div class="pdp-top-bar">
            <a class="pdp-back-link" routerLink="/projects" aria-label="Back to projects">
              <span class="material-symbols-rounded" aria-hidden="true">arrow_back</span>
              Projects
            </a>

            @if (p.status === 'ARCHIVED') {
              <app-button variant="ghost" size="sm"
                          [loading]="restoring()"
                          (click)="restoreProject()"
                          aria-label="Restore project"
                          title="Restore project"
                          style="color: var(--color-accent);">
                <span class="material-symbols-rounded" aria-hidden="true">settings_backup_restore</span>
              </app-button>
            } @else {
              <app-button variant="danger" size="sm"
                          [loading]="archiving()"
                          (click)="archiveProject()"
                          aria-label="Archive project"
                          title="Archive project">
                <span class="material-symbols-rounded" aria-hidden="true">inventory_2</span>
              </app-button>
            }
          </div>

          <!-- Project name + dot (inline editable) -->
          <div class="pdp-project-title-row">
            <span
              class="pdp-project-dot"
              [style.background]="accentColor()"
              [style.box-shadow]="accentShadow()"
              aria-hidden="true"
            ></span>
            @if (editingName()) {
              <input
                class="pdp-name-input"
                [(ngModel)]="editNameValue"
                (keydown.enter)="commitName()"
                (keydown.escape)="cancelName()"
                (blur)="commitName()"
                [attr.aria-label]="'Edit project name'"
                autofocus
              />
            } @else {
              <h1
                class="pdp-project-name pdp-editable"
                (click)="startEditName()"
                title="Click to edit name"
                tabindex="0"
                (keydown.enter)="startEditName()"
                (keydown.space)="startEditName()"
                role="button"
                aria-label="Project name — click to edit"
              >
                {{ p.name }}
                <span class="pdp-edit-hint material-symbols-rounded" aria-hidden="true">edit</span>
              </h1>
            }
          </div>

          <!-- Inline save error -->
          @if (inlineSaveError()) {
            <p class="pdp-inline-error">
              <span class="material-symbols-rounded" aria-hidden="true">error</span>
              {{ inlineSaveError() }}
            </p>
          }

          <!-- Status + visibility (visibility is clickable to cycle) -->
          <div class="pdp-meta-row">
            <app-project-status-badge [status]="p.status" />
            <button
              class="pdp-visibility-chip pdp-visibility-chip--btn"
              (click)="cycleVisibility()"
              [attr.aria-label]="'Visibility: ' + p.visibility + ' — click to change'"
              title="Click to change visibility"
            >
              @if (savingInline()) {
                <span class="pdp-saving-dot" aria-hidden="true"></span>
              } @else {
                <span class="material-symbols-rounded" aria-hidden="true">{{ visibilityIcon() }}</span>
              }
              {{ visibilityLabel() }}
            </button>
          </div>

          <!-- Description (inline editable) -->
          @if (editingDesc()) {
            <textarea
              class="pdp-desc-textarea"
              [(ngModel)]="editDescValue"
              (keydown.escape)="cancelDesc()"
              (blur)="commitDesc()"
              rows="3"
              placeholder="Add a description…"
              aria-label="Edit project description"
            ></textarea>
          } @else {
            <p
              class="pdp-description pdp-editable"
              [class.pdp-description--empty]="!p.description"
              (click)="startEditDesc()"
              title="Click to edit description"
              tabindex="0"
              (keydown.enter)="startEditDesc()"
              (keydown.space)="startEditDesc()"
              role="button"
              aria-label="Project description — click to edit"
            >
              {{ p.description || 'Add a description…' }}
              <span class="pdp-edit-hint material-symbols-rounded" aria-hidden="true">edit</span>
            </p>
          }

          <!-- Created date -->
          <div class="pdp-date">
            <span class="material-symbols-rounded" aria-hidden="true">calendar_today</span>
            <span>Created {{ p.createdAt | date: 'MMM d, yyyy' }}</span>
          </div>

          <div class="pdp-divider" aria-hidden="true"></div>

          <!-- ── Team assignment ─────────────────── -->
          <div class="pdp-team-row" aria-label="Team assignment">

            <span class="material-symbols-rounded pdp-team-row-icon" aria-hidden="true">group</span>

            @if (assignedTeam() && !selectingTeam()) {
              <!-- Team assigned — show name + edit on hover -->
              <span class="pdp-team-row-name">{{ assignedTeam()!.teamName ?? 'Assigned team' }}</span>
              <button class="pdp-team-change-btn" (click)="selectingTeam.set(true)"
                      title="Change team" aria-label="Change assigned team">
                <span class="material-symbols-rounded" aria-hidden="true">edit</span>
              </button>

            } @else if (selectingTeam() || !assignedTeam()) {

              @if (teams().length === 0) {
                <!-- No teams at all -->
                <span class="pdp-team-row-empty">
                  No teams yet — <a routerLink="/teams/new" class="pdp-link">create one</a>
                </span>

              } @else if (!selectingTeam()) {
                <!-- No team assigned, not yet clicked — invite to click -->
                <button class="pdp-team-unassigned" (click)="selectingTeam.set(true)">
                  No team assigned
                </button>

              } @else {
                <!-- Selecting mode — auto-assign on change -->
                <select class="pdp-team-select"
                        [ngModel]="selectedTeamId()"
                        (ngModelChange)="onTeamSelectChange($event)"
                        aria-label="Select a team"
                        [disabled]="assigningTeam()">
                  <option value="">Select a team…</option>
                  @for (t of teams(); track t.id) {
                    <option [value]="t.id">{{ t.name }}</option>
                  }
                </select>
                @if (assigningTeam()) {
                  <span class="pdp-team-spinner" aria-hidden="true"></span>
                } @else {
                  <button class="pdp-team-cancel-btn" (click)="selectingTeam.set(false)" aria-label="Cancel">
                    <span class="material-symbols-rounded" aria-hidden="true">close</span>
                  </button>
                }
              }

            }

            @if (assignTeamSuccess()) {
              <span class="pdp-team-ok" role="status">
                <span class="material-symbols-rounded" aria-hidden="true">check_circle</span>
              </span>
            }
            @if (assignTeamError()) {
              <span class="pdp-team-err" role="alert">{{ assignTeamError() }}</span>
            }

          </div>



        </aside>

        <!-- ══ RIGHT PANEL ═════════════════════════════════ -->
        <main class="pdp-right" aria-label="Tasks">

          <div class="pdp-right-header">
            <h2 class="pdp-tasks-title">Tasks</h2>
            <div class="pdp-right-header-spacer" aria-hidden="true"></div>
            <app-button variant="ghost" size="sm"
                        [routerLink]="['/projects', p.id, 'board']"
                        aria-label="Open board view">
              <span class="material-symbols-rounded" aria-hidden="true">view_kanban</span>
              Board
            </app-button>
            <app-button variant="primary" size="sm"
                        (click)="taskPanel()?.openTaskDrawer('TODO')"
                        aria-label="Create new task">
              <span class="material-symbols-rounded" aria-hidden="true">add</span>
              New task
            </app-button>
          </div>

          <!-- Task list + AI drawer wrapper -->
          <div class="pdp-right-body">

            <!-- AI drawer -->
            @if (aiDrawerOpen()) {
              <div class="pdp-ai-drawer" role="complementary" aria-label="AI Assistant">

                <!-- Drawer header -->
                <div class="pdp-ai-drawer-header">
                  <span class="material-symbols-rounded pdp-ai-drawer-star" aria-hidden="true">auto_awesome</span>
                  <span class="pdp-ai-drawer-title">
                    {{ aiMode() === 'generate' ? 'Generate tasks' : aiMode() === 'summarize' ? 'Summarize' : 'AI Chat' }}
                  </span>
                  <button class="pdp-ai-drawer-close" (click)="closeAiDrawer()" aria-label="Close AI panel">
                    <span class="material-symbols-rounded" aria-hidden="true">close</span>
                  </button>
                </div>

                <!-- Drawer body -->
                <div class="pdp-ai-drawer-body">

                  @if (aiError()) {
                    <app-error-banner [message]="aiError()!" />
                  }

                  @if (aiMode() === 'generate') {
                    <app-button variant="secondary" size="sm"
                                [loading]="generating()"
                                (click)="generateTasks()">
                      <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
                      {{ generating() ? 'Generating…' : 'Generate tasks' }}
                    </app-button>

                    @if (draftTasks().length > 0) {
                      <div class="pdp-drafts">
                        <div class="pdp-drafts-header">
                          <span class="pdp-drafts-label">
                            <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
                            {{ draftTasks().length }} suggested tasks
                          </span>
                          <app-button variant="primary" size="sm"
                                      [loading]="savingTasks()"
                                      (click)="saveAllTasks()">
                            <span class="material-symbols-rounded" aria-hidden="true">save</span>
                            {{ savingTasks() ? 'Saving…' : 'Save all' }}
                          </app-button>
                        </div>
                        @if (saveSuccess()) {
                          <div class="pdp-success-banner" role="status">
                            <span class="material-symbols-rounded" aria-hidden="true">check_circle</span>
                            {{ draftTasks().length }} tasks saved.
                          </div>
                        }
                        <div class="pdp-drafts-list">
                          @for (task of draftTasks(); track task.title) {
                            <app-ai-draft-task-item [task]="task" />
                          }
                        </div>
                      </div>
                    }
                  }

                  @if (aiMode() === 'summarize') {
                    <app-button variant="secondary" size="sm"
                                [loading]="summarizing()"
                                (click)="summarizeProject()">
                      <span class="material-symbols-rounded" aria-hidden="true">summarize</span>
                      {{ summarizing() ? 'Summarizing…' : 'Summarize project' }}
                    </app-button>
                    @if (summary()) {
                      <div class="pdp-summary-result">
                        <p class="pdp-summary-text">{{ summary() }}</p>
                      </div>
                    }
                  }

                  @if (aiMode() === 'chat') {
                    <app-ai-chat [projectId]="p.id" />
                  }

                </div>
              </div>
            }

            <app-task-panel [projectId]="p.id" />
          </div>

          <!-- AI FAB -->
          <div class="pdp-fab-wrap">
            @if (fabMenuOpen()) {
              <!-- backdrop -->
              <div class="pdp-fab-backdrop" (click)="fabMenuOpen.set(false)" aria-hidden="true"></div>
              <!-- mini menu -->
              <div class="pdp-fab-menu" role="menu">
                <button class="pdp-fab-item" role="menuitem" (click)="openAiDrawer('generate')">
                  <span class="material-symbols-rounded" aria-hidden="true">task_alt</span>
                  Generate tasks
                </button>
                <button class="pdp-fab-item" role="menuitem" (click)="openAiDrawer('summarize')">
                  <span class="material-symbols-rounded" aria-hidden="true">summarize</span>
                  Summarize
                </button>
                <button class="pdp-fab-item" role="menuitem" (click)="openAiDrawer('chat')">
                  <span class="material-symbols-rounded" aria-hidden="true">chat</span>
                  Chat
                </button>
              </div>
            }
            <button
              class="pdp-fab"
              [class.pdp-fab--active]="fabMenuOpen() || aiDrawerOpen()"
              (click)="toggleFabMenu()"
              aria-label="AI Assistant"
              [attr.aria-expanded]="fabMenuOpen()"
            >
              <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
            </button>
          </div>

        </main>

      </div>

    }

    <style>
      /* ── Host fills the main area absolutely ─────── */
      :host {
        display: block;
        position: absolute;
        inset: 0;
        overflow: hidden;
      }

      /* ── Shell ───────────────────────────────────── */
      .pdp-shell {
        display: flex;
        height: 100%;
        overflow: hidden;
      }

      /* ── Loading / error wrappers ─────────────────── */
      .pdp-loading,
      .pdp-error {
        padding: 2rem;
        width: 100%;
      }

      /* ── Left panel ──────────────────────────────── */
      .pdp-left {
        width: 50%;
        min-width: 280px;
        max-width: 560px;
        flex-shrink: 0;
        border-right: 1px solid var(--color-border);
        overflow-y: auto;
        padding: 1.5rem 1.5rem 2rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        scrollbar-width: thin;
        scrollbar-color: var(--color-border) transparent;
      }
      .pdp-left::-webkit-scrollbar { width: 4px; }
      .pdp-left::-webkit-scrollbar-track { background: transparent; }
      .pdp-left::-webkit-scrollbar-thumb {
        background: var(--color-border);
        border-radius: 9999px;
      }

      /* ── Top bar (back link + archive action) ────── */
      .pdp-top-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }

      /* ── Back link ───────────────────────────────── */
      .pdp-back-link {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-text-muted);
        text-decoration: none;
        transition: color 0.15s ease;
      }
      .pdp-back-link:hover { color: var(--color-text-primary); }
      .pdp-back-link .material-symbols-rounded { font-size: 1rem; }

      /* ── Top action button (archive / restore) ───── */
      .pdp-top-action {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.375rem;
        border: none;
        background: transparent;
        cursor: pointer;
        opacity: 0.55;
        transition: opacity 0.15s ease, background 0.15s ease, color 0.15s ease;
        padding: 0;
        flex-shrink: 0;
      }
      .pdp-top-action .material-symbols-rounded { font-size: 1rem; }
      .pdp-top-action:hover { opacity: 1; }
      .pdp-top-action:disabled { opacity: 0.3; cursor: not-allowed; }

      .pdp-top-action--archive {
        color: var(--color-text-muted);
      }
      .pdp-top-action--archive:hover {
        background: color-mix(in oklch, var(--color-danger) 10%, transparent);
        color: var(--color-danger);
      }

      .pdp-top-action--restore {
        color: var(--color-text-muted);
      }
      .pdp-top-action--restore:hover {
        background: color-mix(in oklch, var(--color-accent) 10%, transparent);
        color: var(--color-accent);
      }



      /* ── Project title ───────────────────────────── */
      .pdp-project-title-row {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        min-width: 0;
      }

      .pdp-project-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        flex-shrink: 0;
      }

      .pdp-project-name {
        font-family: 'Outfit', sans-serif;
        font-size: 1.25rem;
        font-weight: 700;
        color: var(--color-text-primary);
        margin: 0;
        letter-spacing: -0.02em;
        line-height: 1.25;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      /* ── Inline editable shared ──────────────────── */
      .pdp-editable {
        cursor: pointer;
        border-radius: 0.375rem;
        transition: background 0.15s ease;
        position: relative;
      }
      .pdp-editable:hover,
      .pdp-editable:focus {
        background: color-mix(in oklch, var(--color-accent) 6%, transparent);
        outline: none;
      }
      .pdp-edit-hint {
        font-size: 0.75rem;
        opacity: 0;
        color: var(--color-text-muted);
        margin-left: 0.25rem;
        vertical-align: middle;
        transition: opacity 0.15s ease;
      }
      .pdp-editable:hover .pdp-edit-hint,
      .pdp-editable:focus .pdp-edit-hint {
        opacity: 1;
      }

      /* ── Name input ──────────────────────────────── */
      .pdp-name-input {
        flex: 1;
        min-width: 0;
        font-family: 'Outfit', sans-serif;
        font-size: 1.25rem;
        font-weight: 700;
        color: var(--color-text-primary);
        letter-spacing: -0.02em;
        line-height: 1.25;
        background: var(--color-bg-elevated);
        border: 1.5px solid var(--color-accent);
        border-radius: 0.375rem;
        padding: 0.125rem 0.5rem;
        outline: none;
        box-shadow: 0 0 0 3px var(--color-accent-subtle);
        width: 100%;
      }

      /* ── Description textarea ────────────────────── */
      .pdp-desc-textarea {
        width: 100%;
        font-family: 'Outfit', sans-serif;
        font-size: 0.875rem;
        line-height: 1.6;
        color: var(--color-text-primary);
        background: var(--color-bg-elevated);
        border: 1.5px solid var(--color-accent);
        border-radius: 0.375rem;
        padding: 0.375rem 0.5rem;
        outline: none;
        resize: vertical;
        box-shadow: 0 0 0 3px var(--color-accent-subtle);
        box-sizing: border-box;
      }

      /* ── Inline error ────────────────────────────── */
      .pdp-inline-error {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.75rem;
        color: var(--color-danger);
        margin: 0;
      }
      .pdp-inline-error .material-symbols-rounded { font-size: 0.875rem; }

      /* ── Saving dot ──────────────────────────────── */
      .pdp-saving-dot {
        display: inline-block;
        width: 0.625rem;
        height: 0.625rem;
        border-radius: 50%;
        border: 1.5px solid currentColor;
        border-top-color: transparent;
        animation: pdp-spin 0.6s linear infinite;
      }
      @keyframes pdp-spin {
        to { transform: rotate(360deg); }
      }

      /* ── Meta row ────────────────────────────────── */
      .pdp-meta-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-wrap: wrap;
      }

      .pdp-visibility-chip {
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
      .pdp-visibility-chip .material-symbols-rounded { font-size: 0.75rem; }

      .pdp-visibility-chip--btn {
        cursor: pointer;
        font-family: inherit;
        transition: background 0.15s ease, border-color 0.15s ease;
      }
      .pdp-visibility-chip--btn:hover:not(:disabled) {
        background: color-mix(in oklch, var(--color-accent) 10%, var(--color-bg-overlay));
        border-color: color-mix(in oklch, var(--color-accent) 35%, transparent);
        color: var(--color-text-primary);
      }
      .pdp-visibility-chip--btn:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }

      /* ── Description ─────────────────────────────── */
      .pdp-description {
        font-size: 0.875rem;
        line-height: 1.6;
        color: var(--color-text-secondary);
        margin: 0;
        padding: 0.125rem 0.375rem;
        white-space: pre-wrap;
        word-break: break-word;
      }
      .pdp-description--empty {
        color: var(--color-text-muted);
        font-style: italic;
      }

      /* ── Date ────────────────────────────────────── */
      .pdp-date {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.75rem;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
        font-feature-settings: 'tnum';
      }
      .pdp-date .material-symbols-rounded { font-size: 0.875rem; }

      /* ── Divider ─────────────────────────────────── */
      .pdp-divider {
        height: 1px;
        background: color-mix(in oklch, var(--color-border) 60%, transparent);
        flex-shrink: 0;
      }

      /* ── Section shell ───────────────────────────── */
      .pdp-section {
        border-radius: 0.875rem;
        border: 1px solid var(--color-border);
        background: color-mix(in oklch, var(--color-bg-surface) 80%, transparent);
        overflow: hidden;
      }

      .pdp-section-header {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.875rem 1rem 0.75rem;
        border-bottom: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
      }

      .pdp-section-icon {
        flex-shrink: 0;
        width: 2rem;
        height: 2rem;
        border-radius: 0.5rem;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .pdp-section-icon .material-symbols-rounded { font-size: 1rem; }

      .pdp-section-icon--teams {
        background: color-mix(in oklch, var(--color-success) 12%, var(--color-bg-elevated));
        border: 1px solid color-mix(in oklch, var(--color-success) 20%, transparent);
      }
      .pdp-section-icon--teams .material-symbols-rounded { color: var(--color-success); }

      .pdp-section-icon--ai {
        background: color-mix(in oklch, var(--color-accent) 12%, var(--color-bg-elevated));
        border: 1px solid color-mix(in oklch, var(--color-accent) 20%, transparent);
      }
      .pdp-section-icon--ai .material-symbols-rounded { color: var(--color-accent); }

      .pdp-section-meta {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
        min-width: 0;
      }

      .pdp-section-title {
        font-family: 'Outfit', sans-serif;
        font-size: 0.875rem;
        font-weight: 650;
        color: var(--color-text-primary);
        margin: 0;
        letter-spacing: -0.01em;
      }

      .pdp-section-desc {
        font-size: 0.75rem;
        color: var(--color-text-muted);
        margin: 0;
        line-height: 1.4;
      }

      .pdp-section-body {
        padding: 0.875rem 1rem 1rem;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }

      /* ── Team row (inline, no card) ─────────────── */
      .pdp-team-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0;
      }

      .pdp-team-row-icon {
        font-size: 0.9375rem;
        color: var(--color-text-muted);
        flex-shrink: 0;
      }

      .pdp-team-row-name {
        font-size: 0.875rem;
        font-weight: 500;
        color: var(--color-text-primary);
        flex: 1;
        min-width: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .pdp-team-row-empty {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        font-style: italic;
      }

      .pdp-team-unassigned {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        font-style: italic;
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        font-family: inherit;
        text-align: left;
        transition: color 0.15s;
      }
      .pdp-team-unassigned:hover { color: var(--color-accent); }

      .pdp-team-spinner {
        display: block;
        width: 0.875rem;
        height: 0.875rem;
        border-radius: 9999px;
        flex-shrink: 0;
        animation: pdp-spin 0.65s linear infinite;
        background: conic-gradient(from 0deg, var(--color-text-muted) 0%, transparent 70%);
        mask: radial-gradient(farthest-side, transparent 55%, black 56%);
        -webkit-mask: radial-gradient(farthest-side, transparent 55%, black 56%);
      }

      .pdp-team-change-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.375rem;
        height: 1.375rem;
        border-radius: 0.3rem;
        border: none;
        background: transparent;
        color: var(--color-text-muted);
        cursor: pointer;
        flex-shrink: 0;
        padding: 0;
        opacity: 0;
        transition: opacity 0.15s, color 0.15s;
      }
      .pdp-team-row:hover .pdp-team-change-btn { opacity: 1; }
      .pdp-team-change-btn .material-symbols-rounded { font-size: 0.875rem; }
      .pdp-team-change-btn:hover { color: var(--color-accent); }

      .pdp-team-cancel-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.375rem;
        height: 1.375rem;
        border-radius: 0.3rem;
        border: none;
        background: transparent;
        color: var(--color-text-muted);
        cursor: pointer;
        flex-shrink: 0;
        padding: 0;
        transition: color 0.15s;
      }
      .pdp-team-cancel-btn .material-symbols-rounded { font-size: 0.875rem; }
      .pdp-team-cancel-btn:hover { color: var(--color-danger); }

      .pdp-team-select {
        flex: 1;
        min-width: 0;
        padding: 0.3125rem 0.625rem;
        border-radius: 0.4rem;
        border: 1px solid var(--color-border);
        background: var(--color-bg-elevated);
        color: var(--color-text-primary);
        font-size: 0.8125rem;
        font-family: 'Outfit', sans-serif;
        cursor: pointer;
        outline: none;
        appearance: none;
        transition: border-color 0.15s;
      }
      .pdp-team-select:focus {
        border-color: var(--color-accent);
        box-shadow: 0 0 0 2px var(--color-accent-subtle);
      }
      .pdp-team-select option {
        background: var(--color-bg-elevated);
        color: var(--color-text-primary);
      }

      .pdp-team-ok {
        display: flex;
        align-items: center;
        color: var(--color-success);
        font-size: 0.875rem;
        flex-shrink: 0;
      }
      .pdp-team-ok .material-symbols-rounded { font-size: 1rem; }

      .pdp-team-err {
        font-size: 0.75rem;
        color: var(--color-danger);
      }

      .pdp-empty-text {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        margin: 0;
      }

      .pdp-link {
        color: var(--color-accent);
        text-decoration: underline;
        text-underline-offset: 2px;
      }
      .pdp-link:hover { color: var(--color-accent-hover); }

      /* ── Success banner ──────────────────────────── */
      .pdp-success-banner {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.75rem;
        border-radius: 0.5rem;
        background: var(--color-success-subtle);
        border: 1px solid color-mix(in oklch, var(--color-success) 25%, transparent);
        color: var(--color-success);
        font-size: 0.8125rem;
        font-weight: 500;
      }
      .pdp-success-banner .material-symbols-rounded { font-size: 1rem; }

      /* ── Action buttons row ──────────────────────── */
      .pdp-actions {
        display: flex;
        gap: 0.5rem;
      }

      .pdp-archive-confirm {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      .pdp-archive-confirm-text {
        font-size: 0.8125rem;
        color: var(--color-text-secondary);
        white-space: nowrap;
      }

      /* ── AI drawer ───────────────────────────────── */
      .pdp-ai-drawer {
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        width: min(380px, 100%);
        background: var(--color-bg-surface);
        border-left: 1px solid var(--color-border);
        display: flex;
        flex-direction: column;
        z-index: 10;
        box-shadow: -4px 0 24px oklch(0 0 0 / 0.08);
      }

      .pdp-ai-drawer-header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 1rem 1.25rem;
        border-bottom: 1px solid var(--color-border);
        flex-shrink: 0;
      }

      .pdp-ai-drawer-star {
        font-size: 1rem;
        color: var(--color-accent);
      }

      .pdp-ai-drawer-title {
        flex: 1;
        font-family: 'Outfit', sans-serif;
        font-size: 0.875rem;
        font-weight: 650;
        color: var(--color-text-primary);
        letter-spacing: -0.01em;
      }

      .pdp-ai-drawer-close {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 0.3rem;
        border: none;
        background: transparent;
        color: var(--color-text-muted);
        cursor: pointer;
        padding: 0;
        transition: color 0.15s, background 0.15s;
      }
      .pdp-ai-drawer-close .material-symbols-rounded { font-size: 1rem; }
      .pdp-ai-drawer-close:hover {
        background: color-mix(in oklch, var(--color-border) 60%, transparent);
        color: var(--color-text-primary);
      }

      .pdp-ai-drawer-body {
        flex: 1;
        overflow-y: auto;
        padding: 1rem 1.25rem;
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
        scrollbar-width: thin;
        scrollbar-color: var(--color-border) transparent;
      }

      /* ── FAB ─────────────────────────────────────── */
      .pdp-fab-wrap {
        position: absolute;
        bottom: 1.5rem;
        right: 1.5rem;
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.5rem;
        z-index: 20;
      }

      .pdp-fab-backdrop {
        position: fixed;
        inset: 0;
        z-index: 19;
      }

      .pdp-fab {
        width: 2.75rem;
        height: 2.75rem;
        border-radius: 9999px;
        border: none;
        background: var(--color-accent);
        color: #fff;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 4px 16px oklch(from var(--color-accent) l c h / 0.4);
        transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.15s;
        position: relative;
        z-index: 21;
      }
      .pdp-fab .material-symbols-rounded { font-size: 1.25rem; }
      .pdp-fab:hover {
        transform: scale(1.06);
        box-shadow: 0 6px 20px oklch(from var(--color-accent) l c h / 0.5);
      }
      .pdp-fab--active {
        background: var(--color-accent);
        transform: rotate(20deg) scale(1.06);
      }

      .pdp-fab-menu {
        background: var(--color-bg-elevated);
        border: 1px solid var(--color-border);
        border-radius: 0.75rem;
        padding: 0.375rem;
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
        box-shadow: 0 8px 24px oklch(0 0 0 / 0.12);
        position: relative;
        z-index: 21;
        min-width: 160px;
      }

      .pdp-fab-item {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.75rem;
        border-radius: 0.5rem;
        border: none;
        background: transparent;
        color: var(--color-text-primary);
        font-size: 0.8125rem;
        font-family: 'Outfit', sans-serif;
        cursor: pointer;
        text-align: left;
        transition: background 0.12s;
        white-space: nowrap;
      }
      .pdp-fab-item .material-symbols-rounded {
        font-size: 1rem;
        color: var(--color-accent);
        flex-shrink: 0;
      }
      .pdp-fab-item:hover {
        background: color-mix(in oklch, var(--color-accent) 8%, transparent);
      }

      /* ── Draft tasks ─────────────────────────────── */
      .pdp-drafts {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        padding: 0.75rem;
        border-radius: 0.5rem;
        background: color-mix(in oklch, var(--color-accent) 4%, var(--color-bg-elevated));
        border: 1px solid color-mix(in oklch, var(--color-accent) 15%, transparent);
      }

      .pdp-drafts-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }

      .pdp-drafts-label {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--color-accent);
        font-family: 'Outfit', sans-serif;
        letter-spacing: 0.01em;
      }
      .pdp-drafts-label .material-symbols-rounded { font-size: 0.875rem; }

      .pdp-drafts-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      /* ── Summary result ──────────────────────────── */
      .pdp-summary-result {
        display: flex;
        gap: 0.625rem;
        align-items: flex-start;
        padding: 0.75rem;
        border-radius: 0.5rem;
        background: color-mix(in oklch, var(--color-bg-elevated) 80%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 60%, transparent);
      }

      .pdp-summary-icon {
        font-size: 1.125rem;
        color: var(--color-cyan);
        flex-shrink: 0;
        margin-top: 0.0625rem;
      }

      .pdp-summary-text {
        font-size: 0.8125rem;
        color: var(--color-text-secondary);
        line-height: 1.6;
        margin: 0;
      }

      /* ══ RIGHT PANEL ══════════════════════════════════ */
      .pdp-right {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }

      .pdp-right-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1.25rem 1.5rem 0;
        border-bottom: 1px solid var(--color-border);
        padding-bottom: 0.875rem;
        flex-shrink: 0;
      }

      .pdp-tasks-title {
        font-family: 'Outfit', sans-serif;
        font-size: 1rem;
        font-weight: 650;
        color: var(--color-text-primary);
        margin: 0;
        letter-spacing: -0.01em;
      }

      .pdp-right-header-spacer {
        flex: 1;
      }

      .pdp-right-body {
        flex: 1;
        overflow: auto;
        min-height: 0;
        position: relative;
      }
    </style>
  `,
})
export class ProjectDetailPageComponent {
  private readonly route          = inject(ActivatedRoute);
  private readonly router         = inject(Router);
  private readonly projectService = inject(ProjectService);
  private readonly aiService      = inject(AiService);
  private readonly taskService    = inject(TaskService);
  private readonly teamService    = inject(TeamService);

  readonly taskPanel        = viewChild(TaskPanelComponent);

  readonly project          = signal<Project | null>(null);
  readonly loading          = signal(true);
  readonly error            = signal<string | null>(null);
  readonly aiExpanded       = signal(false);
  readonly fabMenuOpen      = signal(false);
  readonly aiDrawerOpen     = signal(false);
  readonly aiMode           = signal<'generate' | 'summarize' | 'chat'>('generate');

  // Team assignment
  readonly teams             = signal<Team[]>([]);
  readonly selectedTeamId    = signal<string>('');
  readonly assigningTeam     = signal(false);
  readonly assignTeamSuccess = signal(false);
  readonly assignTeamError   = signal<string | null>(null);
  readonly selectingTeam     = signal(false);

  readonly assignedTeam = computed<ProjectTeamAssignment | null>(() => {
    const projectTeams = this.project()?.teams;
    if (!projectTeams || projectTeams.length === 0) return null;
    const first = projectTeams[0];
    // Enrich with teamName from the loaded teams list
    const match = this.teams().find(t => t.id === first.teamId);
    return { teamId: first.teamId, teamName: match?.name ?? first.teamName };
  });

  // AI assistant
  readonly generating       = signal(false);
  readonly summarizing      = signal(false);
  readonly savingTasks      = signal(false);
  readonly saveSuccess      = signal(false);
  readonly draftTasks       = signal<TaskDraft[]>([]);
  readonly summary          = signal<string | null>(null);
  readonly aiError          = signal<string | null>(null);

  // ── Inline edit state ─────────────────────────────────────────────────
  readonly editingName      = signal(false);
  readonly editingDesc      = signal(false);
  readonly savingInline     = signal(false);
  readonly savingField      = signal<'name' | 'description' | 'visibility' | null>(null);
  readonly inlineSaveError  = signal<string | null>(null);
  readonly archiving = signal(false);
  readonly restoring         = signal(false);
  editNameValue             = '';
  editDescValue             = '';

  startEditName(): void {
    this.editNameValue = this.project()?.name ?? '';
    this.editingName.set(true);
    this.inlineSaveError.set(null);
  }

  cancelName(): void {
    this.editingName.set(false);
  }

  commitName(): void {
    const name = this.editNameValue.trim();
    this.editingName.set(false);
    if (!name || name === this.project()?.name) return;
    this.saveInline({ name });
  }

  startEditDesc(): void {
    this.editDescValue = this.project()?.description ?? '';
    this.editingDesc.set(true);
    this.inlineSaveError.set(null);
  }

  cancelDesc(): void {
    this.editingDesc.set(false);
  }

  commitDesc(): void {
    const desc = this.editDescValue.trim();
    this.editingDesc.set(false);
    if (desc === (this.project()?.description ?? '')) return;
    this.saveInline({ description: desc });
  }

  cycleVisibility(): void {
    // Use the optimistic value as current if there's a pending change not yet confirmed
    const current = (this.optimisticVisibility() ?? this.project()?.visibility ?? 'PRIVATE') as Visibility;
    const next: Record<Visibility, Visibility> = {
      PRIVATE: 'TEAM',
      TEAM:    'PUBLIC',
      PUBLIC:  'PRIVATE',
    };
    const nextVis = next[current];
    this.optimisticVisibility.set(nextVis);
    this.saveInline({ visibility: nextVis });
  }

  // Pending patch — accumulates all field changes while a save is in-flight.
  // When the in-flight save finishes, the accumulated patch is sent as one single PATCH.
  private pendingPatch: { name?: string; description?: string; visibility?: string } | null = null;
  private isSaving = false;
  // Optimistic visibility — shows the intended value immediately, before server confirms
  readonly optimisticVisibility = signal<Visibility | null>(null);

  private flushPending(): void {
    if (!this.pendingPatch) {
      this.savingInline.set(false);
      this.savingField.set(null);
      return;
    }

    const p = this.project();
    if (!p) {
      this.pendingPatch = null;
      this.savingInline.set(false);
      this.savingField.set(null);
      return;
    }

    const patch = this.pendingPatch;
    this.pendingPatch = null;
    this.inlineSaveError.set(null);
    this.isSaving = true;

    const payload = {
      name:        patch.name        ?? p.name,
      description: patch.description !== undefined ? patch.description : p.description,
      visibility:  patch.visibility  ?? p.visibility,
    };

    this.projectService
      .update(p.id, payload as { name: string; description?: string; visibility: string })
      .pipe(catchError(() => {
        this.inlineSaveError.set('Could not save. Please try again.');
        return of(null);
      }))
      .subscribe(updated => {
        if (updated) {
          this.project.set(updated);
          // Clear optimistic value — server response is now the source of truth
          if (!this.pendingPatch?.visibility) {
            this.optimisticVisibility.set(null);
          }
        }
        this.isSaving = false;
        this.flushPending();
      });
  }

  private saveInline(
    patch: { name?: string; description?: string; visibility?: string },
  ): void {
    // Merge into the pending patch — last write wins per field
    this.pendingPatch = { ...this.pendingPatch, ...patch };
    this.savingInline.set(true);

    if (!this.isSaving) {
      this.flushPending();
    }
    // If already saving, flushPending() will be called automatically when the
    // in-flight request completes, picking up the merged pending patch.
  }

  // Accent color computeds
  private readonly hue = computed(() => {
    const name = this.project()?.name ?? '';
    return nameToHue(name);
  });
  accentColor  = computed(() => `oklch(0.68 0.20 ${this.hue()})`);
  accentShadow = computed(() => `0 0 10px oklch(0.68 0.20 ${this.hue()} / 0.5)`);

  visibilityIcon = computed(() => {
    switch (this.optimisticVisibility() ?? this.project()?.visibility) {
      case 'PUBLIC': return 'public';
      case 'TEAM':   return 'group';
      default:       return 'lock';
    }
  });

  visibilityLabel = computed(() => {
    switch (this.optimisticVisibility() ?? this.project()?.visibility) {
      case 'PUBLIC': return 'Public';
      case 'TEAM':   return 'Team';
      default:       return 'Private';
    }
  });

  constructor() {
    const projectId = this.route.snapshot.paramMap.get('projectId');
    if (!projectId) {
      this.error.set('Project ID not found.');
      this.loading.set(false);
      return;
    }
    this.loadProject(projectId);
    this.loadTeams();
  }

  private loadProject(id: string): void {
    this.projectService.getById(id).subscribe({
      next:  (p) => { this.project.set(p); this.loading.set(false); },
      error: ()  => { this.error.set('Failed to load project.'); this.loading.set(false); },
    });
  }

  private loadTeams(): void {
    this.teamService.getAll().subscribe({
      next:  (t) => this.teams.set(t),
      error: ()  => { /* non-critical */ },
    });
  }

  toggleAi(): void {
    this.aiExpanded.update(v => !v);
  }

  toggleFabMenu(): void {
    if (this.aiDrawerOpen()) {
      this.closeAiDrawer();
    } else {
      this.fabMenuOpen.update(v => !v);
    }
  }

  openAiDrawer(mode: 'generate' | 'summarize' | 'chat'): void {
    this.aiMode.set(mode);
    this.aiDrawerOpen.set(true);
    this.fabMenuOpen.set(false);
  }

  closeAiDrawer(): void {
    this.aiDrawerOpen.set(false);
    this.fabMenuOpen.set(false);
  }

  onTeamSelectChange(teamId: string): void {
    this.selectedTeamId.set(teamId);
    if (teamId) this.assignTeam();
  }

  assignTeam(): void {
    const teamId    = this.selectedTeamId();
    const projectId = this.project()?.id;
    if (!teamId || !projectId) return;

    this.assigningTeam.set(true);
    this.assignTeamError.set(null);
    this.assignTeamSuccess.set(false);

    this.projectService.assignTeam(projectId, teamId).subscribe({
      next: () => {
        this.assigningTeam.set(false);
        this.assignTeamSuccess.set(true);
        this.selectedTeamId.set('');
        this.selectingTeam.set(false);
        this.loadProject(projectId);
        setTimeout(() => this.assignTeamSuccess.set(false), 3000);
      },
      error: () => {
        this.assignTeamError.set('Failed to assign team. Please try again.');
        this.assigningTeam.set(false);
      },
    });
  }

  generateTasks(): void {
    const p = this.project();
    if (!p) return;
    this.generating.set(true);
    this.aiError.set(null);
    this.draftTasks.set([]);
    this.saveSuccess.set(false);
    this.aiService.generateTasks(p.id, p.description ?? p.name, true).subscribe({
      next:  (res) => { this.draftTasks.set(res.tasks); this.generating.set(false); },
      error: (err) => {
        this.aiError.set(err.status === 503
          ? 'AI service is temporarily unavailable.'
          : 'Failed to generate tasks. Please try again.');
        this.generating.set(false);
      },
    });
  }

  saveAllTasks(): void {
    const p      = this.project();
    const drafts = this.draftTasks();
    if (!p || drafts.length === 0) return;

    this.savingTasks.set(true);
    this.aiError.set(null);

    const requests = drafts.map(draft =>
      this.taskService.create({
        projectId:   p.id,
        title:       draft.title,
        description: draft.description,
        priority:    draft.priority,
      })
    );

    forkJoin(requests).subscribe({
      next: () => {
        this.savingTasks.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => {
          this.draftTasks.set([]);
          this.saveSuccess.set(false);
        }, 2000);
      },
      error: () => {
        this.aiError.set('Failed to save some tasks. Please try again.');
        this.savingTasks.set(false);
      },
    });
  }

  summarizeProject(): void {
    const p = this.project();
    if (!p) return;
    this.summarizing.set(true);
    this.aiError.set(null);
    this.summary.set(null);
    this.aiService.getProjectSummary(p.id).subscribe({
      next:  (res) => { this.summary.set(res.summary); this.summarizing.set(false); },
      error: ()    => { this.aiError.set('Failed to summarize project.'); this.summarizing.set(false); },
    });
  }

  archiveProject(): void {
    const p = this.project();
    if (!p) return;
    if (!confirm(`Archive "${p.name}"? It will be hidden from the project list.`)) return;
    this.archiving.set(true);
    this.projectService.archive(p.id).subscribe({
      next:  () => this.router.navigate(['/projects']),
      error: () => {
        this.error.set('Failed to archive project. Please try again.');
        this.archiving.set(false);
      },
    });
  }

  restoreProject(): void {
    const id = this.project()?.id;
    if (!id) return;
    this.restoring.set(true);
    this.projectService.restore(id).subscribe({
      next: (updated) => {
        this.project.set(updated);
        this.restoring.set(false);
      },
      error: () => {
        this.error.set('Failed to restore project. Please try again.');
        this.restoring.set(false);
      },
    });
  }
}
