import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TaskStatus, TaskSummary, TASK_STATUS_LABELS } from '../../../../core/models/task.models';
import { TaskCardComponent } from '../task-card/task-card.component';

@Component({
  selector: 'app-kanban-column',
  standalone: true,
  imports: [MatIconModule, TaskCardComponent],
  templateUrl: './kanban-column.component.html',
  styleUrl: './kanban-column.component.scss',
})
export class KanbanColumnComponent {
  status = input.required<TaskStatus>();
  tasks = input<TaskSummary[]>([]);

  get statusLabel(): string {
    return TASK_STATUS_LABELS[this.status()];
  }
}
