import { inject } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { computed } from '@angular/core';
import { pipe, switchMap, tap } from 'rxjs';
import { tapResponse } from '@ngrx/operators';
import {
  TaskStatus,
  TaskSummary,
  KanbanColumn,
  CreateTaskRequest,
  UpdateTaskRequest,
  TASK_STATUS_ORDER,
} from '../../core/models/task.models';
import { TaskService } from './task.service';

interface TaskState {
  kanban: Record<TaskStatus, TaskSummary[]>;
  tasks: TaskSummary[];
  loading: boolean;
  error: string | null;
}

const emptyKanban = (): Record<TaskStatus, TaskSummary[]> => ({
  TODO: [],
  IN_PROGRESS: [],
  IN_REVIEW: [],
  DONE: [],
  CANCELLED: [],
});

const initialState: TaskState = {
  kanban: emptyKanban(),
  tasks: [],
  loading: false,
  error: null,
};

export const TaskStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    kanbanColumns: computed<KanbanColumn[]>(() =>
      TASK_STATUS_ORDER.map((status) => ({
        status,
        tasks: store.kanban()[status] ?? [],
      })),
    ),
    isLoading: computed(() => store.loading()),
  })),
  withMethods((store, taskService = inject(TaskService)) => ({
    loadKanban: rxMethod<string>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap((projectId) =>
          taskService.getKanban(projectId).pipe(
            tapResponse({
              next: (response) =>
                patchState(store, {
                  kanban: response.columns,
                  loading: false,
                }),
              error: (err: unknown) =>
                patchState(store, {
                  loading: false,
                  error: err instanceof Error ? err.message : 'Failed to load kanban',
                }),
            }),
          ),
        ),
      ),
    ),

    createTask: rxMethod<CreateTaskRequest>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap((req) =>
          taskService.create(req).pipe(
            tapResponse({
              next: (task) => {
                const kanban = { ...store.kanban() };
                const col = kanban[task.status] ?? [];
                kanban[task.status] = [
                  ...col,
                  {
                    taskId: task.id,
                    title: task.title,
                    status: task.status,
                    priority: task.priority,
                    assigneeId: task.assigneeId,
                    deadline: task.deadline,
                    parentTaskId: task.parentTaskId,
                  },
                ];
                patchState(store, { kanban, loading: false });
              },
              error: (err: unknown) =>
                patchState(store, {
                  loading: false,
                  error: err instanceof Error ? err.message : 'Failed to create task',
                }),
            }),
          ),
        ),
      ),
    ),

    updateTask: rxMethod<{ taskId: string; req: UpdateTaskRequest }>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(({ taskId, req }) =>
          taskService.update(taskId, req).pipe(
            tapResponse({
              next: (updated) => {
                const kanban = { ...store.kanban() };
                TASK_STATUS_ORDER.forEach((status) => {
                  kanban[status] = (kanban[status] ?? []).map((t) =>
                    t.taskId === taskId
                      ? {
                          ...t,
                          title: updated.title,
                          priority: updated.priority,
                          deadline: updated.deadline,
                        }
                      : t,
                  );
                });
                patchState(store, { kanban, loading: false });
              },
              error: (err: unknown) =>
                patchState(store, {
                  loading: false,
                  error: err instanceof Error ? err.message : 'Failed to update task',
                }),
            }),
          ),
        ),
      ),
    ),

    changeStatus: rxMethod<{ taskId: string; status: TaskStatus }>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(({ taskId, status }) =>
          taskService.changeStatus(taskId, status).pipe(
            tapResponse({
              next: (updated) => {
                const kanban = { ...store.kanban() };
                let movedTask: TaskSummary | undefined;

                // Remove from all columns
                TASK_STATUS_ORDER.forEach((s) => {
                  const idx = (kanban[s] ?? []).findIndex((t) => t.taskId === taskId);
                  if (idx !== -1) {
                    movedTask = kanban[s][idx];
                    kanban[s] = kanban[s].filter((t) => t.taskId !== taskId);
                  }
                });

                // Add to new status column
                if (movedTask) {
                  kanban[updated.status] = [
                    ...(kanban[updated.status] ?? []),
                    { ...movedTask, status: updated.status },
                  ];
                }

                patchState(store, { kanban, loading: false });
              },
              error: (err: unknown) =>
                patchState(store, {
                  loading: false,
                  error: err instanceof Error ? err.message : 'Failed to change status',
                }),
            }),
          ),
        ),
      ),
    ),
  })),
);
