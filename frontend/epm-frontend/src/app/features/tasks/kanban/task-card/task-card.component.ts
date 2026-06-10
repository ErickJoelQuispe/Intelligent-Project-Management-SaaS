import {
  Component,
  ChangeDetectionStrategy,
  input,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { CardComponent } from '../../../../shared/components/card/card.component';
import { AvatarComponent } from '../../../../shared/components/avatar/avatar.component';
import { TaskPriorityBadgeComponent } from '../../../../shared/components/task-priority-badge/task-priority-badge.component';
import { TaskSummary } from '../../../../core/models/task.models';

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
  // priorityColor() eliminado — reemplazado por TaskPriorityBadgeComponent
}
