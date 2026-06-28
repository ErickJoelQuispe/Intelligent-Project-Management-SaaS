import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { TaskPriorityBadgeComponent } from '../task-priority-badge/task-priority-badge.component';
import { TaskDraft } from '../../../features/ai/ai.service';

@Component({
  selector: 'app-ai-draft-task-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TaskPriorityBadgeComponent],
  template: `
    <div
      class="flex flex-col gap-2 p-4 rounded-lg border border-border border-l-4 border-l-accent bg-bg-surface hover:bg-bg-elevated transition-colors duration-150 cursor-pointer"
      [class.draft-selected]="selected()"
      (click)="toggleSelected.emit()"
      role="checkbox"
      [attr.aria-checked]="selected()"
      tabindex="0"
      (keydown.space)="$event.preventDefault(); toggleSelected.emit()"
      (keydown.enter)="toggleSelected.emit()"
    >
      <!-- Title row: checkbox + title + priority badge -->
      <div class="flex items-start justify-between gap-3">
        <div class="flex items-start gap-2 flex-1 min-w-0">
          <!-- Visual checkbox -->
          <span class="draft-checkbox material-symbols-rounded flex-shrink-0 mt-0.5 text-base"
                aria-hidden="true">
            {{ selected() ? 'check_box' : 'check_box_outline_blank' }}
          </span>
          <span class="text-text-primary text-sm font-medium leading-snug">
            {{ task().title }}
          </span>
        </div>
        <app-task-priority-badge [priority]="task().priority" />
      </div>

      @if (task().description) {
        <p class="text-text-muted text-xs leading-relaxed pl-6">
          {{ task().description }}
        </p>
      }

      <div class="flex items-center gap-1 text-text-disabled text-xs pl-6">
        <span class="material-symbols-rounded text-sm" aria-hidden="true">auto_awesome</span>
        <span>AI suggested</span>
      </div>
    </div>

    <style>
      .draft-selected {
        background: color-mix(in oklch, var(--color-accent) 6%, var(--color-bg-elevated));
        border-color: color-mix(in oklch, var(--color-accent) 40%, transparent);
      }
      .draft-checkbox {
        color: var(--color-text-disabled);
        transition: color 0.15s ease;
      }
      .draft-selected .draft-checkbox {
        color: var(--color-accent);
      }
    </style>
  `,
})
export class AiDraftTaskItemComponent {
  task = input.required<TaskDraft>();
  selected = input<boolean>(false);
  toggleSelected = output<void>();
}
