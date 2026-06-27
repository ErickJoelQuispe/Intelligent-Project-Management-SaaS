import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { TaskStatus } from '../../../../core/models/task.models';
import { TenantUser } from '../../../../core/models/user-profile.model';
import { TaskStore } from '../../task.store';
import { TaskService } from '../../task.service';
import { UserService } from '../../../settings/services/user.service';
import { KanbanColumnComponent } from '../kanban-column/kanban-column.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';

@Component({
  selector: 'app-kanban-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    KanbanColumnComponent,
    SpinnerComponent,
    ErrorBannerComponent,
  ],
  providers: [TaskStore],
  template: `
    <div class="kanban-panel-content">

      @if (store.isLoading()) {
        <app-spinner size="lg" [full]="true" label="Loading board..." />

      } @else if (store.error()) {
        <app-error-banner
          [message]="store.error()!"
          retryLabel="Retry"
          (retry)="store.loadKanban(projectId())"
        />

      } @else {

        <div class="kanban-columns" role="region" aria-label="Kanban board columns">
          @for (col of store.kanbanColumns(); track col.status) {
            <app-kanban-column
              [status]="col.status"
              [projectId]="projectId()"
              [tasks]="col.tasks"
              [users]="users()"
              (taskDropped)="onTaskDropped($event)"
              (taskDeleted)="onTaskDeleted($event)"
            />
          }
        </div>

      }

    </div>

    <style>
      .kanban-panel-content {
        padding: 1.25rem 1.5rem 1.5rem;
        display: flex;
        flex-direction: column;
        overflow: hidden;
        height: 100%;
        box-sizing: border-box;
      }

      .kanban-columns {
        display: flex;
        gap: 0.875rem;
        overflow-x: auto;
        overflow-y: visible;
        padding-bottom: 0.75rem;
        align-items: flex-start;
        flex: 1;

        scrollbar-width: thin;
        scrollbar-color: var(--color-border) transparent;
      }
      .kanban-columns::-webkit-scrollbar { height: 4px; }
      .kanban-columns::-webkit-scrollbar-track { background: transparent; }
      .kanban-columns::-webkit-scrollbar-thumb {
        background: var(--color-border);
        border-radius: 9999px;
      }
    </style>
  `,
})
export class KanbanPanelComponent implements OnInit {
  private readonly taskService = inject(TaskService);
  private readonly userService = inject(UserService);
  readonly store = inject(TaskStore);

  projectId = input.required<string>();
  users     = signal<TenantUser[]>([]);

  ngOnInit(): void {
    this.store.loadKanban(this.projectId());
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
      next:  () => this.store.loadKanban(this.projectId()),
      error: () => {},
    });
  }
}
