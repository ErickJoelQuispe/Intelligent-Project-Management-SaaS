import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { TaskService } from '../task.service';
import { Task } from '../../../core/models/task.models';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { TaskStatusBadgeComponent } from '../../../shared/components/task-status-badge/task-status-badge.component';
import { TaskPriorityBadgeComponent } from '../../../shared/components/task-priority-badge/task-priority-badge.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-task-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    TranslocoPipe,
    ErrorBannerComponent,
    EmptyStateComponent,
    TaskStatusBadgeComponent,
    TaskPriorityBadgeComponent,
  ],
  templateUrl: './task-list.component.html',
  styleUrl: './task-list.component.scss',
})
export class TaskListComponent implements OnInit {
  private readonly taskService        = inject(TaskService);
  private readonly route              = inject(ActivatedRoute);
  private readonly confirmDialog      = inject(ConfirmDialogService);
  private readonly translocoService   = inject(TranslocoService);

  readonly tableHeaders = ['Title', 'Status', 'Priority', 'Deadline', ''];

  projectId     = '';
  tasks         = signal<Task[]>([]);
  loading       = signal(false);
  error         = signal<string | null>(null);
  totalElements = signal(0);
  pageSize      = 10;
  pageIndex     = 0;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') ?? '';
    this.loadTasks();
  }

  loadTasks(): void {
    this.loading.set(true);
    this.error.set(null);
    this.taskService.list(this.projectId, this.pageIndex, this.pageSize).subscribe({
      next:  (page) => { this.tasks.set(page.content); this.totalElements.set(page.totalElements); this.loading.set(false); },
      error: ()     => { this.error.set(this.translocoService.translate('tasks.list.loadError')); this.loading.set(false); },
    });
  }

  deleteTask(taskId: string, title: string): void {
    this.confirmDialog.open({
      title: `Delete "${title}"?`,
      message: this.translocoService.translate('tasks.panel.deleteError'),
      confirmLabel: this.translocoService.translate('common.delete'),
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.taskService.delete(taskId).subscribe({
        next:  () => this.loadTasks(),
        error: () => this.error.set(this.translocoService.translate('tasks.panel.deleteError')),
      });
    });
  }

  onPageChange(page: number): void {
    this.pageIndex = page;
    this.loadTasks();
  }

  get totalPages(): number {
    return Math.ceil(this.totalElements() / this.pageSize);
  }

  isOverdue(deadline: string): boolean {
    return new Date(deadline) < new Date();
  }
}
