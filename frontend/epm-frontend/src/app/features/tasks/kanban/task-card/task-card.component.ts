import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  inject,
  computed,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { CardComponent } from '../../../../shared/components/card/card.component';
import { AvatarComponent } from '../../../../shared/components/avatar/avatar.component';
import { TaskPriorityBadgeComponent } from '../../../../shared/components/task-priority-badge/task-priority-badge.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { TaskSummary } from '../../../../core/models/task.models';
import { TenantUser } from '../../../../core/models/user-profile.model';
import { DragStateService } from '../drag/drag-state.service';

@Component({
  selector: 'app-task-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CardComponent, AvatarComponent, TaskPriorityBadgeComponent, ButtonComponent],
  templateUrl: './task-card.component.html',
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  task       = input.required<TaskSummary>();
  users      = input<TenantUser[]>([]);
  deleteTask = output<string>();

  private readonly dragState = inject(DragStateService);

  assigneeName = computed(() => {
    const assigneeId = this.task().assigneeId;
    if (!assigneeId) return null;
    const user = this.users().find((u) => u.id === assigneeId);
    if (!user) return assigneeId;
    return user.firstName && user.lastName
      ? `${user.firstName} ${user.lastName}`
      : user.email;
  });

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

  onDelete(event: MouseEvent): void {
    event.stopPropagation();
    if (!confirm(`Delete "${this.task().title}"? This cannot be undone.`)) return;
    this.deleteTask.emit(this.task().taskId);
  }
}
