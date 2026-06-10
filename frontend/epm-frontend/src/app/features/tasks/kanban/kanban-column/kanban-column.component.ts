import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';
import { TaskStatus, TaskSummary, TASK_STATUS_LABELS } from '../../../../core/models/task.models';
import { TaskCardComponent } from '../task-card/task-card.component';
import { BadgeComponent } from '../../../../shared/components/badge/badge.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';

@Component({
  selector: 'app-kanban-column',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TaskCardComponent, BadgeComponent, EmptyStateComponent],
  templateUrl: './kanban-column.component.html',
  styleUrl: './kanban-column.component.scss',
})
export class KanbanColumnComponent {
  status = input.required<TaskStatus>();
  tasks  = input<TaskSummary[]>([]);

  // computed() en lugar de get — se integra con OnPush correctamente
  statusLabel = computed(() => TASK_STATUS_LABELS[this.status()]);
  taskCount   = computed(() => this.tasks().length);
}
