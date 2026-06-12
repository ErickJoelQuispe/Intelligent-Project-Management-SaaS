import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  inject,
  computed,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { CardComponent } from '../../../../shared/components/card/card.component';
import { AvatarComponent } from '../../../../shared/components/avatar/avatar.component';
import { TaskPriorityBadgeComponent } from '../../../../shared/components/task-priority-badge/task-priority-badge.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { Task, TaskSummary } from '../../../../core/models/task.models';
import { TenantUser } from '../../../../core/models/user-profile.model';
import { DragStateService } from '../drag/drag-state.service';
import { TaskService } from '../../task.service';

@Component({
  selector: 'app-task-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CardComponent, AvatarComponent, TaskPriorityBadgeComponent, ButtonComponent, SpinnerComponent],
  templateUrl: './task-card.component.html',
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  task       = input.required<TaskSummary>();
  users      = input<TenantUser[]>([]);
  deleteTask = output<string>();

  private readonly dragState   = inject(DragStateService);
  private readonly taskService = inject(TaskService);

  // ── Expand state ────────────────────────────────────────────────────────────
  expanded        = signal(false);
  subtasks        = signal<Task[]>([]);
  loadingSubtasks = signal(false);
  newSubtaskTitle = signal('');
  addingSubtask   = signal(false);
  private subtasksLoaded = false;

  assigneeName = computed(() => {
    const assigneeId = this.task().assigneeId;
    if (!assigneeId) return null;
    const user = this.users().find((u) => u.id === assigneeId);
    if (!user) return assigneeId;
    return user.firstName && user.lastName
      ? `${user.firstName} ${user.lastName}`
      : user.email;
  });

  toggleExpand(): void {
    const next = !this.expanded();
    this.expanded.set(next);

    if (next && !this.subtasksLoaded) {
      this.loadingSubtasks.set(true);
      this.taskService.getSubtasks(this.task().taskId).subscribe({
        next: (tasks) => {
          this.subtasks.set(tasks);
          this.loadingSubtasks.set(false);
          this.subtasksLoaded = true;
        },
        error: () => {
          this.loadingSubtasks.set(false);
        },
      });
    }
  }

  addSubtask(): void {
    const title = this.newSubtaskTitle().trim();
    if (!title || this.addingSubtask()) return;

    this.addingSubtask.set(true);
    this.taskService.createSubtask({
      parentTaskId: this.task().taskId,
      title,
      priority: 'MEDIUM',
    }).subscribe({
      next: (created) => {
        this.subtasks.update((list) => [...list, created]);
        this.newSubtaskTitle.set('');
        this.addingSubtask.set(false);
      },
      error: () => {
        this.addingSubtask.set(false);
      },
    });
  }

  deleteSubtask(subtaskId: string, event: MouseEvent): void {
    event.stopPropagation();
    this.taskService.delete(subtaskId).subscribe({
      next: () => {
        this.subtasks.update((list) => list.filter((s) => s.id !== subtaskId));
      },
    });
  }

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
