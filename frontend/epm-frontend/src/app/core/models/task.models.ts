export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'CANCELLED';
export type TaskPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export interface Task {
  id: string;
  tenantId: string;
  projectId: string;
  parentTaskId?: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  deadline?: string;
  assigneeId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TaskSummary {
  taskId: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId?: string;
  deadline?: string;
  parentTaskId?: string;
}

export interface KanbanColumn {
  status: TaskStatus;
  tasks: TaskSummary[];
}

export interface KanbanResponse {
  columns: Record<TaskStatus, TaskSummary[]>;
}

export interface CreateTaskRequest {
  projectId: string;
  title: string;
  description?: string;
  priority: TaskPriority;
  deadline?: string;
  assigneeId?: string;
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  priority?: TaskPriority;
  deadline?: string;
  assigneeId?: string;
}

export interface CreateSubtaskRequest {
  parentTaskId: string;
  projectId: string;
  title: string;
  description?: string;
  priority: TaskPriority;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const TASK_STATUS_ORDER: TaskStatus[] = [
  'TODO',
  'IN_PROGRESS',
  'IN_REVIEW',
  'DONE',
  'CANCELLED',
];

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  IN_REVIEW: 'In Review',
  DONE: 'Done',
  CANCELLED: 'Cancelled',
};
