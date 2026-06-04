export type NotificationType =
  | 'TASK_CREATED'
  | 'TASK_ASSIGNED'
  | 'TASK_STATUS_CHANGED'
  | 'TASK_DELETED';

export interface Notification {
  id: string;
  type: NotificationType;
  referenceId: string;
  message: string;
  read: boolean;
  createdAt: string;
}
