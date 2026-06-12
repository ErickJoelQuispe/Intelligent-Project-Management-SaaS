import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TaskStatus } from '../../../../core/models/task.models';
import { TenantUser } from '../../../../core/models/user-profile.model';
import { TaskStore } from '../../task.store';
import { TaskService } from '../../task.service';
import { UserService } from '../../../settings/services/user.service';
import { KanbanColumnComponent } from '../kanban-column/kanban-column.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';

@Component({
  selector: 'app-kanban-board',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    KanbanColumnComponent,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
  ],
  templateUrl: './kanban-board.component.html',
  styleUrl: './kanban-board.component.scss',
  providers: [TaskStore],
})
export class KanbanBoardComponent implements OnInit {
  private readonly route       = inject(ActivatedRoute);
  private readonly taskService = inject(TaskService);
  private readonly userService = inject(UserService);
  readonly store = inject(TaskStore);

  projectId = '';
  users     = signal<TenantUser[]>([]);

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') ?? '';
    this.store.loadKanban(this.projectId);
    this.userService.listTenantUsers().subscribe({
      next: (u) => this.users.set(u),
      error: () => {},
    });
  }

  onTaskDropped(event: { taskId: string; newStatus: TaskStatus }): void {
    this.store.changeStatus({ taskId: event.taskId, status: event.newStatus });
  }

  onTaskDeleted(taskId: string): void {
    this.taskService.delete(taskId).subscribe({
      next:  () => this.store.loadKanban(this.projectId),
      error: () => {},
    });
  }
}
