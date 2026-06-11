import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CardComponent } from '../card/card.component';
import { ButtonComponent } from '../button/button.component';
import { ProjectStatusBadgeComponent } from '../project-status-badge/project-status-badge.component';
import { Project } from '../../../core/models/project.model';

@Component({
  selector: 'app-project-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, CardComponent, ButtonComponent, ProjectStatusBadgeComponent],
  template: `
    <div class="group relative animate-card-in">

      <!-- Glow en hover — aparece detrás de la card -->
      <div class="absolute inset-0 rounded-xl opacity-0 group-hover:opacity-100
                  transition-opacity duration-500 pointer-events-none -z-10"
           style="background: radial-gradient(ellipse at 50% 0%, oklch(0.65 0.26 285 / 0.15) 0%, transparent 70%);
                  filter: blur(8px); transform: translateY(4px);">
      </div>

      <app-card class="group-hover:border-glow transition-all duration-300 block">

        <!-- Header: borde luminoso superior -->
        <div card-header class="relative overflow-hidden">
          <!-- Línea gradiente en el top del header -->
          <div class="absolute top-0 left-0 right-0 h-px"
               style="background: linear-gradient(90deg, transparent 0%, oklch(0.65 0.26 285 / 0.5) 40%, oklch(0.78 0.18 200 / 0.4) 70%, transparent 100%);">
          </div>

          <div class="flex items-start justify-between gap-3 px-6 pt-5 pb-4">
            <div class="flex flex-col gap-2 min-w-0">
              <h3 class="font-semibold text-base leading-snug truncate"
                  style="color: oklch(0.96 0.006 268); font-family: 'Outfit', sans-serif;">
                {{ project().name }}
              </h3>
              <app-project-status-badge [status]="project().status" />
            </div>

            <span class="material-symbols-rounded text-lg shrink-0 mt-0.5"
                  style="color: oklch(0.42 0.012 268);"
                  [title]="project().visibility">
              {{ project().visibility === 'PUBLIC' ? 'public' :
                 project().visibility === 'TEAM'   ? 'group' : 'lock' }}
            </span>
          </div>
        </div>

        <!-- Body -->
        <div class="flex flex-col gap-4">

          <!-- Descripción -->
          <p class="text-sm leading-relaxed line-clamp-2"
             style="color: oklch(0.55 0.015 268);">
            {{ project().description || 'No description provided.' }}
          </p>

          <!-- Meta -->
          <div class="flex items-center gap-1.5 text-xs"
               style="color: oklch(0.42 0.012 268); font-family: 'JetBrains Mono', monospace;">
            <span class="material-symbols-rounded text-sm">calendar_today</span>
            <span>{{ project().createdAt | date: 'MMM d, yyyy' }}</span>
          </div>

          <!-- Acciones -->
          @if (showActions()) {
            <div class="flex items-center gap-2 pt-3"
                 style="border-top: 1px solid oklch(0.22 0.020 268 / 0.6);">
              <app-button variant="secondary" size="sm"
                          [routerLink]="['/projects', project().id, 'tasks']">
                <span class="material-symbols-rounded text-sm">list</span>
                Tasks
              </app-button>
              <app-button variant="secondary" size="sm"
                          [routerLink]="['/projects', project().id, 'tasks', 'kanban']">
                <span class="material-symbols-rounded text-sm">view_kanban</span>
                Kanban
              </app-button>
              <app-button variant="ghost" size="sm"
                          [routerLink]="['/projects', project().id]"
                          title="AI Assistant & project details"
                          style="margin-left: auto;">
                <span class="material-symbols-rounded text-sm"
                      style="color: oklch(0.65 0.26 285);">auto_awesome</span>
                AI
              </app-button>
              <app-button variant="ghost" size="sm"
                          (click)="onArchive($event)"
                          title="Archive project">
                <span class="material-symbols-rounded text-sm"
                      style="color: oklch(0.65 0.22 25);">archive</span>
              </app-button>
              <ng-content select="[card-actions]" />
            </div>
          }

        </div>
      </app-card>
    </div>
  `,
})
export class ProjectCardComponent {
  project          = input.required<Project>();
  showActions      = input<boolean>(true);
  viewTasks        = output<Project>();
  viewKanban       = output<Project>();
  projectArchived  = output<Project>();

  onArchive(event: MouseEvent): void {
    event.stopPropagation();
    this.projectArchived.emit(this.project());
  }
}
