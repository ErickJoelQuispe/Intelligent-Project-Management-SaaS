import {
  Component,
  ChangeDetectionStrategy,
  input,
  inject,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { CardComponent } from '../../../../shared/components/card/card.component';
import { AvatarComponent } from '../../../../shared/components/avatar/avatar.component';
import { TaskPriorityBadgeComponent } from '../../../../shared/components/task-priority-badge/task-priority-badge.component';
import { TaskSummary } from '../../../../core/models/task.models';
import { DragStateService } from '../drag/drag-state.service';

@Component({
  selector: 'app-task-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CardComponent, AvatarComponent, TaskPriorityBadgeComponent],
  templateUrl: './task-card.component.html',
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  task = input.required<TaskSummary>();

  private readonly dragState = inject(DragStateService);

  onDragStart(event: DragEvent): void {
    const task = this.task();
    // dataTransfer needed for Firefox compatibility
    event.dataTransfer?.setData('text/plain', task.taskId);
    event.dataTransfer!.effectAllowed = 'move';
    this.dragState.start(task.taskId, task.status);

    // Add dragging class to the card element for visual feedback
    (event.currentTarget as HTMLElement).classList.add('dragging');
  }

  onDragEnd(event: DragEvent): void {
    this.dragState.clear();
    (event.currentTarget as HTMLElement).classList.remove('dragging');
  }
}
