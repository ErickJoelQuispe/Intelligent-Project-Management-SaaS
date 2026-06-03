import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { TaskSummary, TaskPriority } from '../../../../core/models/task.models';

@Component({
  selector: 'app-task-card',
  standalone: true,
  imports: [DatePipe, MatCardModule, MatChipsModule, MatIconModule],
  templateUrl: './task-card.component.html',
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  task = input.required<TaskSummary>();

  priorityColor(priority: TaskPriority): string {
    switch (priority) {
      case 'HIGH':
        return 'var(--priority-high, #e53935)';
      case 'MEDIUM':
        return 'var(--priority-medium, #fb8c00)';
      case 'LOW':
        return 'var(--priority-low, #43a047)';
    }
  }
}
