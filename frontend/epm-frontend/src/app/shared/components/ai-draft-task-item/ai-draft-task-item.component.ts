import {
  Component,
  ChangeDetectionStrategy,
  input,
} from '@angular/core';
import { TaskPriorityBadgeComponent } from '../task-priority-badge/task-priority-badge.component';
import { TaskDraft } from '../../../features/ai/ai.service';

@Component({
  selector: 'app-ai-draft-task-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TaskPriorityBadgeComponent],
  template: `
    <!--
      Borde izquierdo de acento + fondo levemente distinto
      comunica visualmente "esto es una sugerencia, no es real todavía"
    -->
    <div class="flex flex-col gap-2 p-4
                rounded-lg border border-border border-l-4 border-l-accent
                bg-bg-surface hover:bg-bg-elevated
                transition-colors duration-150">

      <!-- Título + badge de prioridad en la misma línea -->
      <div class="flex items-start justify-between gap-3">
        <span class="text-text-primary text-sm font-medium leading-snug">
          {{ task().title }}
        </span>
        <app-task-priority-badge [priority]="task().priority" />
      </div>

      <!-- Descripción — solo si existe -->
      @if (task().description) {
        <p class="text-text-muted text-xs leading-relaxed">
          {{ task().description }}
        </p>
      }

      <!-- Indicador de origen IA — pequeño, discreto -->
      <div class="flex items-center gap-1 text-text-disabled text-xs">
        <span class="material-symbols-rounded text-sm">auto_awesome</span>
        <span>AI suggested</span>
      </div>

    </div>
  `,
})
export class AiDraftTaskItemComponent {
  task = input.required<TaskDraft>();
}
