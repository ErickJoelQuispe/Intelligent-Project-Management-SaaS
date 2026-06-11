import { Injectable, signal } from '@angular/core';
import { TaskStatus } from '../../../../core/models/task.models';

export interface DragState {
  taskId:     string;
  fromStatus: TaskStatus;
}

/**
 * Singleton service that holds drag-and-drop state across KanbanColumn instances.
 * Using a service (not the TaskStore) keeps UI state separate from domain state.
 */
@Injectable({ providedIn: 'root' })
export class DragStateService {
  private readonly _dragging = signal<DragState | null>(null);

  readonly dragging = this._dragging.asReadonly();

  start(taskId: string, fromStatus: TaskStatus): void {
    this._dragging.set({ taskId, fromStatus });
  }

  clear(): void {
    this._dragging.set(null);
  }
}
