import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { TaskStatus, TaskSummary, TASK_STATUS_KEYS } from '../../../../core/models/task.models';
import { TenantUser } from '../../../../core/models/user-profile.model';
import { TaskCardComponent } from '../task-card/task-card.component';
import { DragStateService } from '../drag/drag-state.service';

@Component({
  selector: 'app-kanban-column',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TaskCardComponent, TranslocoPipe],
  templateUrl: './kanban-column.component.html',
  styleUrl: './kanban-column.component.scss',
})
export class KanbanColumnComponent {
  status    = input.required<TaskStatus>();
  projectId = input.required<string>();
  tasks     = input<TaskSummary[]>([]);
  users     = input<TenantUser[]>([]);

  /** Emits taskId when a card is dropped onto this column */
  taskDropped  = output<{ taskId: string; newStatus: TaskStatus }>();
  /** Emits taskId when the user confirms deletion of a card */
  taskDeleted  = output<string>();

  private readonly dragState      = inject(DragStateService);
  private readonly translocoService = inject(TranslocoService);

  readonly statusKey   = computed(() => TASK_STATUS_KEYS[this.status()]);
  readonly statusLabel = computed(() => this.translocoService.translate(TASK_STATUS_KEYS[this.status()]));
  taskCount   = computed(() => this.tasks().length);

  /** Visual state — true while a draggable hovers over this column */
  isDragOver = signal(false);

  onDragOver(event: DragEvent): void {
    // Must preventDefault to allow drop
    event.preventDefault();
    event.dataTransfer!.dropEffect = 'move';
  }

  onDragEnter(event: DragEvent): void {
    event.preventDefault();
    const dragging = this.dragState.dragging();
    // Only highlight if dragging from a different column
    if (dragging && dragging.fromStatus !== this.status()) {
      this.isDragOver.set(true);
    }
  }

  onDragLeave(event: DragEvent): void {
    // Only clear when leaving the column container itself, not a child element
    const target   = event.currentTarget as HTMLElement;
    const related  = event.relatedTarget as HTMLElement | null;
    if (!related || !target.contains(related)) {
      this.isDragOver.set(false);
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(false);

    const dragging = this.dragState.dragging();
    if (!dragging) return;
    if (dragging.fromStatus === this.status()) return; // same column — no-op

    this.taskDropped.emit({ taskId: dragging.taskId, newStatus: this.status() });
    this.dragState.clear();
  }
}
