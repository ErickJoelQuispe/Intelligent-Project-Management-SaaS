import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  viewChild,
  HostListener,
  ElementRef,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
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
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

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

const PROJECT_COLOR_PALETTE = [
  '#ef4444', '#f97316', '#eab308', '#22c55e',
  '#14b8a6', '#3b82f6', '#8b5cf6', '#ec4899',
  '#64748b', '#0ea5e9', '#a855f7', '#f43f5e',
  '#10b981', '#6366f1', '#f59e0b', '#84cc16',
];

interface SummaryData {
  status: string;
  risks: string[];
  milestones: string[];
}

@Component({
  selector: 'app-project-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    FormsModule,
    DatePipe,
    TranslocoPipe,
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
            <a class="pdp-back-link" routerLink="/projects" [attr.aria-label]="'projects.detail.backToProjects' | transloco">
              <span class="material-symbols-rounded" aria-hidden="true">arrow_back</span>
              {{ 'projects.detail.backToProjects' | transloco }}
            </a>

            @if (p.status === 'ARCHIVED') {
              <app-button variant="ghost" size="sm"
                           [loading]="restoring()"
                           (click)="restoreProject()"
                           [attr.aria-label]="'projects.detail.restoreProject' | transloco"
                           [attr.title]="'projects.detail.restoreProject' | transloco"
                          style="color: var(--color-accent);">
                <span class="material-symbols-rounded" aria-hidden="true">settings_backup_restore</span>
              </app-button>
            } @else {
              <app-button variant="danger" size="sm"
                           [loading]="archiving()"
                           (click)="archiveProject()"
                           [attr.aria-label]="'projects.detail.archiveProject' | transloco"
                           [attr.title]="'projects.detail.archiveProject' | transloco">
                <span class="material-symbols-rounded" aria-hidden="true">inventory_2</span>
              </app-button>
            }
          </div>

          <!-- Project name + dot (inline editable) -->
          <div class="pdp-project-title-row">
            <div class="pdp-dot-wrap">
              <button
                class="pdp-project-dot pdp-project-dot--btn"
                [style.background]="accentColor()"
                [style.box-shadow]="accentShadow()"
                (click)="toggleColorPicker($event)"
                [attr.aria-label]="'Change project color'"
                title="Change color"
                type="button"
              ></button>
              @if (colorPickerOpen()) {
                <div class="pdp-color-picker" role="dialog" aria-label="Pick project color">
                  <div class="pdp-color-palette">
                    @for (c of palette; track c) {
                      <button
                        class="pdp-palette-swatch"
                        [class.pdp-palette-swatch--active]="customColor() === c"
                        [style.background]="c"
                        (click)="pickColor(c)"
                        [attr.aria-label]="'Select color ' + c"
                        type="button"
                      ></button>
                    }
                  </div>
                  <div class="pdp-color-hex-row">
                    <span class="pdp-color-preview" [style.background]="hexInputValue()"></span>
                    <input
                      class="pdp-color-hex-input"
                      type="text"
                      [value]="hexInputValue()"
                      (input)="onHexInput($event)"
                      maxlength="7"
                      placeholder="#000000"
                      aria-label="Hex color value"
                      spellcheck="false"
                    />
                    <input
                      class="pdp-color-native"
                      type="color"
                      [value]="hexInputValue()"
                      (input)="onNativeColorInput($event)"
                      aria-label="Open color wheel"
                      title="Open color wheel"
                    />
                  </div>
                </div>
              }
            </div>
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
                 [attr.aria-label]="'projects.detail.editProjectName' | transloco"
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
              {{ visibilityKey() | transloco }}
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
              [attr.aria-label]="'projects.detail.editDescription' | transloco"
            >
              {{ p.description || ('projects.detail.noDescription' | transloco) }}
              <span class="pdp-edit-hint material-symbols-rounded" aria-hidden="true">edit</span>
            </p>
          }

          <!-- Created date -->
          <div class="pdp-date">
            <span class="material-symbols-rounded" aria-hidden="true">calendar_today</span>
            <span>{{ 'projects.detail.createdAt' | transloco }} {{ p.createdAt | date: 'MMM d, yyyy' }}</span>
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
                  {{ 'projects.detail.noTeamsYet' | transloco }} <a routerLink="/teams/new" class="pdp-link">{{ 'projects.detail.createTeam' | transloco }}</a>
                </span>

              } @else if (!selectingTeam()) {
                <!-- No team assigned, not yet clicked — invite to click -->
                <button class="pdp-team-unassigned" (click)="selectingTeam.set(true)">
                  {{ 'projects.detail.noTeam' | transloco }}
                </button>

              } @else {
                <!-- Selecting mode — auto-assign on change -->
                <select class="pdp-team-select"
                        [ngModel]="selectedTeamId()"
                        (ngModelChange)="onTeamSelectChange($event)"
                         [attr.aria-label]="'projects.detail.selectTeam' | transloco"
                        [disabled]="assigningTeam()">
                  <option value="">{{ 'projects.detail.selectTeam' | transloco }}</option>
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
            <h2 class="pdp-tasks-title">{{ 'projects.detail.tasks' | transloco }}</h2>
            <div class="pdp-right-header-spacer" aria-hidden="true"></div>
            <app-button variant="ghost" size="sm"
                        [routerLink]="['/projects', p.id, 'board']"
                        [attr.aria-label]="'tasks.kanban.openBoardView' | transloco">
              <span class="material-symbols-rounded" aria-hidden="true">view_kanban</span>
              {{ 'projects.detail.board' | transloco }}
            </app-button>
            <app-button variant="primary" size="sm"
                        (click)="taskPanel()?.openTaskDrawer('TODO')"
                        [attr.aria-label]="'projects.detail.newTask' | transloco">
              <span class="material-symbols-rounded" aria-hidden="true">add</span>
              {{ 'projects.detail.newTask' | transloco }}
            </app-button>
          </div>

          <!-- Task list -->
          <div class="pdp-right-body">
            <app-task-panel [projectId]="p.id" />
          </div>

          <!-- AI FAB -->
          <div class="pdp-fab-wrap">
            <button
              class="pdp-fab"
              [class.pdp-fab--active]="aiPanelOpen()"
              (click)="toggleAiPanel()"
              [attr.aria-label]="'projects.ai.aiAssistant' | transloco"
              [attr.aria-expanded]="aiPanelOpen()"
            >
              <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
            </button>
          </div>

        </main>

        <!-- ══ AI PANEL (third column) ═══════════════════════ -->
        @if (aiPanelOpen()) {
          <aside class="pdp-ai-panel" aria-label="AI Assistant">

            <!-- Panel header with mode tabs -->
            <div class="pdp-ai-panel-header">
              <span class="material-symbols-rounded pdp-ai-panel-icon" aria-hidden="true">auto_awesome</span>
              <div class="pdp-ai-panel-tabs" role="tablist" [attr.aria-label]="'projects.ai.aiAssistant' | transloco">
                <button class="pdp-ai-tab" [class.pdp-ai-tab--active]="aiMode() === 'generate'"
                        role="tab" [attr.aria-selected]="aiMode() === 'generate'"
                        (click)="aiMode.set('generate')">
                  <span class="material-symbols-rounded" aria-hidden="true">task_alt</span>
                  {{ 'projects.ai.generate' | transloco }}
                </button>
                <button class="pdp-ai-tab" [class.pdp-ai-tab--active]="aiMode() === 'summarize'"
                        role="tab" [attr.aria-selected]="aiMode() === 'summarize'"
                        (click)="aiMode.set('summarize')">
                  <span class="material-symbols-rounded" aria-hidden="true">summarize</span>
                  {{ 'projects.ai.summarize' | transloco }}
                </button>
                <button class="pdp-ai-tab" [class.pdp-ai-tab--active]="aiMode() === 'chat'"
                        role="tab" [attr.aria-selected]="aiMode() === 'chat'"
                        (click)="aiMode.set('chat')">
                  <span class="material-symbols-rounded" aria-hidden="true">chat</span>
                  {{ 'projects.ai.chat' | transloco }}
                </button>
              </div>
              <button class="pdp-ai-panel-close" (click)="closeAiPanel()" [attr.aria-label]="'projects.ai.closePanel' | transloco">
                <span class="material-symbols-rounded" aria-hidden="true">close</span>
              </button>
            </div>

            <!-- Panel body — scrollable -->
            <div class="pdp-ai-panel-body">

              @if (aiError()) {
                <app-error-banner [message]="aiError()!" />
              }

              @if (aiMode() === 'generate') {
                <app-button variant="secondary" size="sm"
                            [loading]="generating()"
                            (click)="generateTasks()">
                  <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
                  {{ generating() ? ('projects.ai.generating' | transloco) : ('projects.ai.generateTasks' | transloco) }}
                </app-button>

                @if (draftTasks().length > 0) {
                  <div class="pdp-drafts">
                    <div class="pdp-drafts-header">
                      <span class="pdp-drafts-label">
                        <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
                        {{ 'projects.ai.suggestedTasks' | transloco: { count: draftTasks().length } }}
                      </span>
                      <app-button variant="primary" size="sm"
                                  [loading]="savingTasks()"
                                  [disabled]="savingTasks()"
                                  (click)="saveSelectedTasks()">
                        <span class="material-symbols-rounded" aria-hidden="true">save</span>
                        {{ savingTasks() ? ('projects.ai.saving' | transloco) : selectedDraftCount() > 0 ? ('projects.ai.selected' | transloco: { count: selectedDraftCount() }) : ('projects.ai.saveAll' | transloco) }}
                      </app-button>
                    </div>
                    <div class="pdp-drafts-select-all">
                      @if (selectedDraftCount() === draftTasks().length) {
                        <button class="pdp-drafts-select-btn" (click)="clearDraftSelection()">
                          {{ 'projects.ai.clearSelection' | transloco }}
                        </button>
                      } @else {
                        <button class="pdp-drafts-select-btn" (click)="selectAllDrafts()">
                          {{ 'projects.ai.selectAll' | transloco }}
                        </button>
                      }
                      @if (selectedDraftCount() > 0) {
                        <span class="pdp-drafts-select-count">{{ 'projects.ai.selected' | transloco: { count: selectedDraftCount() } }}</span>
                      }
                    </div>
                    @if (saveSuccess()) {
                      <div class="pdp-success-banner" role="status">
                        <span class="material-symbols-rounded" aria-hidden="true">check_circle</span>
                        {{ 'projects.ai.tasksSaved' | transloco }}
                      </div>
                    }
                    <div class="pdp-drafts-list">
                      @for (task of draftTasks(); track task.title; let i = $index) {
                        <app-ai-draft-task-item
                          [task]="task"
                          [selected]="selectedDraftIndices().has(i)"
                          (toggleSelected)="toggleDraftTask(i)"
                        />
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
                  {{ summarizing() ? ('projects.ai.summarizing' | transloco) : ('projects.ai.summarizeProject' | transloco) }}
                </app-button>
                @if (summaryData(); as sd) {
                  <div class="pdp-summary-card">

                    <!-- Status -->
                    <div class="pdp-summary-status">
                      <span class="material-symbols-rounded pdp-summary-status-icon" aria-hidden="true">info</span>
                      <p class="pdp-summary-status-text">{{ sd.status }}</p>
                    </div>

                    <!-- Risks -->
                    @if (sd.risks.length > 0) {
                      <div class="pdp-summary-section">
                        <div class="pdp-summary-section-header pdp-summary-section-header--risk">
                          <span class="material-symbols-rounded" aria-hidden="true">warning</span>
                          {{ 'projects.ai.risks' | transloco }}
                        </div>
                        <ul class="pdp-summary-list">
                          @for (risk of sd.risks; track risk) {
                            <li class="pdp-summary-list-item pdp-summary-list-item--risk">{{ risk }}</li>
                          }
                        </ul>
                      </div>
                    }

                    <!-- Milestones -->
                    @if (sd.milestones.length > 0) {
                      <div class="pdp-summary-section">
                        <div class="pdp-summary-section-header pdp-summary-section-header--milestone">
                          <span class="material-symbols-rounded" aria-hidden="true">flag</span>
                          {{ 'projects.ai.nextMilestones' | transloco }}
                        </div>
                        <ul class="pdp-summary-list">
                          @for (ms of sd.milestones; track ms; let i = $index) {
                            <li class="pdp-summary-list-item pdp-summary-list-item--milestone">
                              <span class="pdp-summary-step">{{ i + 1 }}</span>
                              {{ ms }}
                            </li>
                          }
                        </ul>
                      </div>
                    }

                    <!-- Use as description action -->
                    <div class="pdp-summary-actions">
                      <app-button variant="secondary" size="sm" (click)="applysummaryAsDescription(sd.status)">
                        <span class="material-symbols-rounded" aria-hidden="true">edit_note</span>
                        {{ 'projects.ai.useAsDescription' | transloco }}
                      </app-button>
                    </div>

                  </div>
                }
              }

              @if (aiMode() === 'chat') {
                <app-ai-chat
                  [projectId]="p.id"
                  [projectTasks]="taskPanel()?.tasks()?.map(t => ({ title: t.title, status: t.status })) ?? []"
                />
              }

            </div>

          </aside>
        }

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

      .pdp-dot-wrap {
        position: relative;
        flex-shrink: 0;
        display: flex;
        align-items: center;
      }

      .pdp-project-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        flex-shrink: 0;
      }

      .pdp-project-dot--btn {
        border: none;
        padding: 0;
        cursor: pointer;
        transition: transform 0.15s ease, box-shadow 0.15s ease;
        outline-offset: 3px;
      }
      .pdp-project-dot--btn:hover {
        transform: scale(1.45);
      }
      .pdp-project-dot--btn:focus-visible {
        outline: 2px solid var(--color-accent);
      }

      /* ── Color picker popover ────────────────────── */
      .pdp-color-picker {
        position: absolute;
        top: calc(100% + 10px);
        left: -4px;
        z-index: 100;
        background: var(--color-bg-elevated);
        border: 1px solid var(--color-border);
        border-radius: 0.75rem;
        padding: 0.75rem;
        box-shadow: 0 8px 32px color-mix(in oklch, var(--color-border) 80%, transparent),
                    0 2px 8px color-mix(in oklch, var(--color-border) 40%, transparent);
        display: flex;
        flex-direction: column;
        gap: 0.625rem;
        width: 220px;
        animation: pdp-picker-in 0.15s cubic-bezier(0.2, 0, 0, 1) both;
      }

      @keyframes pdp-picker-in {
        from { opacity: 0; transform: translateY(-6px) scale(0.97); }
        to   { opacity: 1; transform: translateY(0) scale(1); }
      }

      .pdp-color-palette {
        display: grid;
        grid-template-columns: repeat(8, 1fr);
        gap: 0.375rem;
      }

      .pdp-palette-swatch {
        width: 100%;
        aspect-ratio: 1;
        border-radius: 50%;
        border: 2px solid transparent;
        cursor: pointer;
        padding: 0;
        transition: transform 0.12s ease, border-color 0.12s ease;
      }
      .pdp-palette-swatch:hover {
        transform: scale(1.2);
      }
      .pdp-palette-swatch--active {
        border-color: var(--color-bg-elevated);
        outline: 2px solid currentColor;
        outline-offset: 1px;
        transform: scale(1.15);
      }

      .pdp-color-hex-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding-top: 0.375rem;
        border-top: 1px solid color-mix(in oklch, var(--color-border) 60%, transparent);
      }

      .pdp-color-preview {
        width: 1.25rem;
        height: 1.25rem;
        border-radius: 50%;
        flex-shrink: 0;
        border: 1px solid color-mix(in oklch, var(--color-border) 80%, transparent);
        transition: background 0.1s;
      }

      .pdp-color-hex-input {
        flex: 1;
        min-width: 0;
        font-family: 'JetBrains Mono', monospace;
        font-size: 0.75rem;
        color: var(--color-text-primary);
        background: var(--color-bg-surface);
        border: 1px solid var(--color-border);
        border-radius: 0.375rem;
        padding: 0.25rem 0.5rem;
        outline: none;
        transition: border-color 0.15s;
      }
      .pdp-color-hex-input:focus {
        border-color: var(--color-accent);
        box-shadow: 0 0 0 2px var(--color-accent-subtle);
      }

      .pdp-color-native {
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 50%;
        border: 1px solid var(--color-border);
        cursor: pointer;
        padding: 0;
        background: none;
        flex-shrink: 0;
        appearance: none;
        overflow: hidden;
      }
      .pdp-color-native::-webkit-color-swatch-wrapper { padding: 0; }
      .pdp-color-native::-webkit-color-swatch { border: none; border-radius: 50%; }
      .pdp-color-native::-moz-color-swatch { border: none; border-radius: 50%; }

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

      .pdp-summary-actions {
        display: flex;
        padding-top: 0.25rem;
        border-top: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
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

      /* ── AI panel (native split column) ─────────── */
      .pdp-ai-panel {
        width: 420px;
        flex-shrink: 0;
        border-left: 1px solid var(--color-border);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        background: var(--color-bg-surface);
        animation: pdp-ai-slide-in 0.22s cubic-bezier(0.2, 0, 0, 1) both;
      }

      @keyframes pdp-ai-slide-in {
        from { width: 0; opacity: 0; }
        to   { width: 420px; opacity: 1; }
      }

      .pdp-ai-panel-header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem 1rem;
        border-bottom: 1px solid var(--color-border);
        flex-shrink: 0;
      }

      .pdp-ai-panel-icon {
        font-size: 1rem;
        color: var(--color-accent);
        flex-shrink: 0;
      }

      .pdp-ai-panel-tabs {
        flex: 1;
        display: flex;
        gap: 0.125rem;
        background: color-mix(in oklch, var(--color-bg-overlay) 60%, transparent);
        border-radius: 0.5rem;
        padding: 0.1875rem;
      }

      .pdp-ai-tab {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.25rem;
        padding: 0.3125rem 0.5rem;
        border-radius: 0.375rem;
        border: none;
        background: transparent;
        color: var(--color-text-muted);
        font-size: 0.6875rem;
        font-family: 'Outfit', sans-serif;
        font-weight: 500;
        cursor: pointer;
        transition: background 0.15s ease, color 0.15s ease;
        white-space: nowrap;
      }

      .pdp-ai-tab .material-symbols-rounded {
        font-size: 0.8125rem;
      }

      .pdp-ai-tab:hover {
        color: var(--color-text-primary);
        background: color-mix(in oklch, var(--color-bg-elevated) 80%, transparent);
      }

      .pdp-ai-tab--active {
        background: var(--color-bg-elevated);
        color: var(--color-text-primary);
        box-shadow: 0 1px 3px color-mix(in oklch, var(--color-border) 60%, transparent);
      }

      .pdp-ai-panel-close {
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
        transition: color 0.15s ease, background 0.15s ease;
        flex-shrink: 0;
      }

      .pdp-ai-panel-close .material-symbols-rounded {
        font-size: 1rem;
      }

      .pdp-ai-panel-close:hover {
        background: color-mix(in oklch, var(--color-border) 60%, transparent);
        color: var(--color-text-primary);
      }

      .pdp-ai-panel-body {
        flex: 1;
        overflow-y: auto;
        padding: 1rem;
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

      .pdp-drafts-select-all {
        display: flex;
        align-items: center;
        gap: 0.625rem;
      }

      .pdp-drafts-select-btn {
        font-size: 0.6875rem;
        font-weight: 500;
        color: var(--color-accent);
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        font-family: 'Outfit', sans-serif;
        text-decoration: underline;
        text-underline-offset: 2px;
        transition: opacity 0.15s ease;
      }

      .pdp-drafts-select-btn:hover {
        opacity: 0.75;
      }

      .pdp-drafts-select-count {
        font-size: 0.6875rem;
        color: var(--color-text-muted);
        font-family: 'JetBrains Mono', monospace;
      }

      /* ── Summary card ────────────────────────────── */
      .pdp-summary-card {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        animation: fade-up 0.2s cubic-bezier(0.2, 0, 0, 1) both;
      }

      .pdp-summary-status {
        display: flex;
        gap: 0.625rem;
        align-items: flex-start;
        padding: 0.75rem;
        border-radius: 0.625rem;
        background: color-mix(in oklch, var(--color-cyan) 6%, var(--color-bg-elevated));
        border: 1px solid color-mix(in oklch, var(--color-cyan) 20%, transparent);
      }

      .pdp-summary-status-icon {
        font-size: 1rem;
        color: var(--color-cyan);
        flex-shrink: 0;
        margin-top: 0.0625rem;
      }

      .pdp-summary-status-text {
        font-size: 0.8125rem;
        color: var(--color-text-secondary);
        line-height: 1.55;
        margin: 0;
      }

      .pdp-summary-section {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      .pdp-summary-section-header {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        font-family: 'JetBrains Mono', monospace;
      }

      .pdp-summary-section-header .material-symbols-rounded {
        font-size: 0.875rem;
      }

      .pdp-summary-section-header--risk {
        color: var(--color-warning);
      }

      .pdp-summary-section-header--milestone {
        color: var(--color-accent);
      }

      .pdp-summary-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }

      .pdp-summary-list-item {
        display: flex;
        align-items: flex-start;
        gap: 0.5rem;
        font-size: 0.8125rem;
        color: var(--color-text-secondary);
        line-height: 1.45;
        padding: 0.4375rem 0.625rem;
        border-radius: 0.4375rem;
        background: color-mix(in oklch, var(--color-bg-elevated) 70%, transparent);
        border: 1px solid color-mix(in oklch, var(--color-border) 50%, transparent);
      }

      .pdp-summary-list-item--risk {
        border-left: 2px solid color-mix(in oklch, var(--color-warning) 60%, transparent);
      }

      .pdp-summary-list-item--milestone {
        border-left: 2px solid color-mix(in oklch, var(--color-accent) 60%, transparent);
      }

      .pdp-summary-step {
        flex-shrink: 0;
        width: 1.125rem;
        height: 1.125rem;
        border-radius: 50%;
        background: color-mix(in oklch, var(--color-accent) 15%, transparent);
        color: var(--color-accent);
        font-size: 0.625rem;
        font-weight: 700;
        font-family: 'JetBrains Mono', monospace;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
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
  private readonly route            = inject(ActivatedRoute);
  private readonly router           = inject(Router);
  private readonly projectService   = inject(ProjectService);
  private readonly aiService        = inject(AiService);
  private readonly taskService      = inject(TaskService);
  private readonly teamService      = inject(TeamService);
  private readonly confirmDialog    = inject(ConfirmDialogService);
  private readonly translocoService = inject(TranslocoService);
  private readonly elRef            = inject(ElementRef);

  readonly taskPanel        = viewChild(TaskPanelComponent);

  readonly project          = signal<Project | null>(null);
  readonly loading          = signal(true);
  readonly error            = signal<string | null>(null);
  readonly aiPanelOpen      = signal(false);
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
  readonly generating             = signal(false);
  readonly summarizing            = signal(false);
  readonly savingTasks            = signal(false);
  readonly saveSuccess            = signal(false);
  readonly draftTasks             = signal<TaskDraft[]>([]);
  readonly summary                = signal<string | null>(null);
  readonly summaryData            = computed<SummaryData | null>(() => {
    const raw = this.summary();
    if (!raw) return null;
    try {
      // Strip markdown fences the model might add
      const cleaned = raw.replace(/^```(?:json)?\n?/i, '').replace(/\n?```$/i, '').trim();
      const parsed = JSON.parse(cleaned);
      // Happy path: model returned the expected structure
      if (parsed.status && Array.isArray(parsed.risks) && Array.isArray(parsed.milestones)) {
        return parsed as SummaryData;
      }
      // Fallback: model wrapped text in {"summary": "..."} — extract and show as status
      if (typeof parsed.summary === 'string') {
        return { status: parsed.summary, risks: [], milestones: [] };
      }
    } catch { /* fall through */ }
    // Could not parse at all — return null so the UI shows nothing broken
    return null;
  });
  readonly aiError                = signal<string | null>(null);
  readonly selectedDraftIndices   = signal<Set<number>>(new Set());
  readonly selectedDraftCount     = computed(() => this.selectedDraftIndices().size);

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
        this.inlineSaveError.set(this.translocoService.translate('projects.detail.saveError'));
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

  // ── Color picker ──────────────────────────────────────────────────────
  readonly palette = PROJECT_COLOR_PALETTE;
  readonly colorPickerOpen = signal(false);
  readonly customColor     = signal<string | null>(null);
  readonly hexInputValue   = signal<string>('#000000');

  private loadStoredColor(projectId: string): void {
    const stored = localStorage.getItem(`project-color:${projectId}`);
    if (stored) {
      this.customColor.set(stored);
      this.hexInputValue.set(stored);
    } else {
      // Derive a default hex from the name-based hue so the input shows something
      this.customColor.set(null);
      this.hexInputValue.set(this.hue2hex(nameToHue(this.project()?.name ?? '')));
    }
  }

  private hue2hex(hue: number): string {
    // Convert oklch(0.68 0.20 hue) -> rough hex via canvas (simple approximation)
    // We just return a reasonable fallback using HSL since oklch→hex requires a full conversion
    const l = Math.round(68 / 100 * 100);
    const s = 70;
    return this.hslToHex(hue, s, l);
  }

  private hslToHex(h: number, s: number, l: number): string {
    s /= 100; l /= 100;
    const k = (n: number) => (n + h / 30) % 12;
    const a = s * Math.min(l, 1 - l);
    const f = (n: number) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
    const toHex = (x: number) => Math.round(x * 255).toString(16).padStart(2, '0');
    return `#${toHex(f(0))}${toHex(f(8))}${toHex(f(4))}`;
  }

  toggleColorPicker(event: MouseEvent): void {
    event.stopPropagation();
    this.colorPickerOpen.update(v => !v);
  }

  pickColor(hex: string): void {
    const id = this.project()?.id;
    if (!id) return;
    this.customColor.set(hex);
    this.hexInputValue.set(hex);
    localStorage.setItem(`project-color:${id}`, hex);
    this.colorPickerOpen.set(false);
  }

  onHexInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.hexInputValue.set(raw);
    if (/^#[0-9a-fA-F]{6}$/.test(raw)) {
      const id = this.project()?.id;
      if (id) {
        this.customColor.set(raw);
        localStorage.setItem(`project-color:${id}`, raw);
      }
    }
  }

  onNativeColorInput(event: Event): void {
    const hex = (event.target as HTMLInputElement).value;
    this.hexInputValue.set(hex);
    const id = this.project()?.id;
    if (id) {
      this.customColor.set(hex);
      localStorage.setItem(`project-color:${id}`, hex);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.colorPickerOpen()) return;
    const picker = (this.elRef.nativeElement as HTMLElement).querySelector('.pdp-color-picker');
    const dotBtn = (this.elRef.nativeElement as HTMLElement).querySelector('.pdp-project-dot--btn');
    if (
      picker && !picker.contains(event.target as Node) &&
      dotBtn && !dotBtn.contains(event.target as Node)
    ) {
      this.colorPickerOpen.set(false);
    }
  }

  // Accent color computeds
  private readonly hue = computed(() => {
    const name = this.project()?.name ?? '';
    return nameToHue(name);
  });
  accentColor  = computed(() => {
    const custom = this.customColor();
    return custom ?? `oklch(0.68 0.20 ${this.hue()})`;
  });
  accentShadow = computed(() => {
    const color = this.accentColor();
    // Use color-mix for shadow if it's a hex; fallback to oklch
    return `0 0 10px ${color}80`;
  });

  visibilityIcon = computed(() => {
    switch (this.optimisticVisibility() ?? this.project()?.visibility) {
      case 'PUBLIC': return 'public';
      case 'TEAM':   return 'group';
      default:       return 'lock';
    }
  });

  visibilityKey = computed(() => {
    switch (this.optimisticVisibility() ?? this.project()?.visibility) {
      case 'PUBLIC': return 'projects.detail.visibilityPublic';
      case 'TEAM':   return 'projects.detail.visibilityTeam';
      default:       return 'projects.detail.visibilityPrivate';
    }
  });

  constructor() {
    const projectId = this.route.snapshot.paramMap.get('projectId');
    if (!projectId) {
      this.error.set(this.translocoService.translate('projects.detail.notFound'));
      this.loading.set(false);
      return;
    }
    this.loadProject(projectId);
    this.loadTeams();
  }

  private loadProject(id: string): void {
    this.projectService.getById(id).subscribe({
      next:  (p) => {
        this.project.set(p);
        this.loadStoredColor(p.id);
        this.loading.set(false);
      },
      error: ()  => { this.error.set(this.translocoService.translate('projects.form.loadError')); this.loading.set(false); },
    });
  }

  private loadTeams(): void {
    this.teamService.getAll().subscribe({
      next:  (t) => this.teams.set(t),
      error: ()  => { /* non-critical */ },
    });
  }

  toggleAiPanel(): void {
    if (this.aiPanelOpen()) {
      this.closeAiPanel();
    } else {
      this.aiMode.set('generate');
      this.aiPanelOpen.set(true);
    }
  }

  openAiPanel(mode: 'generate' | 'summarize' | 'chat'): void {
    this.aiMode.set(mode);
    this.aiPanelOpen.set(true);
  }

  closeAiPanel(): void {
    this.aiPanelOpen.set(false);
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
        this.assignTeamError.set(this.translocoService.translate('projects.detail.assignTeamError'));
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
    this.selectedDraftIndices.set(new Set());
    this.saveSuccess.set(false);
    const existingTitles = this.taskPanel()?.tasks().map(t => t.title) ?? [];
    this.aiService.generateTasks(p.id, p.description ?? p.name, true, existingTitles).subscribe({
      next:  (res) => { this.draftTasks.set(res.tasks); this.generating.set(false); },
      error: (err) => {
        this.aiError.set(err.status === 503
          ? this.translocoService.translate('projects.ai.unavailable')
          : this.translocoService.translate('projects.ai.generateError'));
        this.generating.set(false);
      },
    });
  }

  toggleDraftTask(index: number): void {
    this.selectedDraftIndices.update(set => {
      const next = new Set(set);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  selectAllDrafts(): void {
    this.selectedDraftIndices.set(new Set(this.draftTasks().map((_, i) => i)));
  }

  clearDraftSelection(): void {
    this.selectedDraftIndices.set(new Set());
  }

  saveSelectedTasks(): void {
    const p      = this.project();
    const drafts = this.draftTasks();
    if (!p || drafts.length === 0) return;

    const indices = this.selectedDraftIndices();
    // If nothing selected, save all
    const toSave = indices.size > 0
      ? drafts.filter((_, i) => indices.has(i))
      : drafts;

    this.savingTasks.set(true);
    this.aiError.set(null);

    const requests = toSave.map(draft =>
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
        // Remove saved tasks from draft list
        const saved = new Set(indices.size > 0 ? [...indices] : drafts.map((_, i) => i));
        this.draftTasks.update(prev => prev.filter((_, i) => !saved.has(i)));
        this.selectedDraftIndices.set(new Set());
        // Refresh the task panel so newly created tasks appear immediately
        this.taskPanel()?.loadTasks();
        setTimeout(() => this.saveSuccess.set(false), 2000);
      },
      error: () => {
        this.aiError.set(this.translocoService.translate('projects.ai.saveTasksError'));
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
      error: ()    => { this.aiError.set(this.translocoService.translate('projects.ai.summarizeError')); this.summarizing.set(false); },
    });
  }

  applysummaryAsDescription(statusText: string): void {
    this.editDescValue = statusText;
    this.commitDesc();
  }

  archiveProject(): void {
    const p = this.project();
    if (!p) return;
    this.confirmDialog.open({
      title: this.translocoService.translate('projects.archive.confirmTitle', { name: p.name }),
      message: this.translocoService.translate('projects.archive.confirmMessage'),
      confirmLabel: this.translocoService.translate('projects.archive.confirmBtn'),
      variant: 'warning',
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.archiving.set(true);
      this.projectService.archive(p.id).subscribe({
        next:  () => this.router.navigate(['/projects']),
        error: () => {
          this.error.set(this.translocoService.translate('projects.list.archiveError'));
          this.archiving.set(false);
        },
      });
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
        this.error.set(this.translocoService.translate('projects.list.restoreError'));
        this.restoring.set(false);
      },
    });
  }
}
