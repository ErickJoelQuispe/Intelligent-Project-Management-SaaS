export type NotificationType =
  | 'TASK_CREATED'
  | 'TASK_ASSIGNED'
  | 'TASK_STATUS_CHANGED'
  | 'TASK_DELETED'
  | 'PROJECT_CREATED'
  | 'PROJECT_ARCHIVED'
  | 'TEAM_ASSIGNED_TO_PROJECT'
  | 'MEMBER_JOINED_TEAM'
  | 'MEMBER_LEFT_TEAM';

export type NotificationChannel = 'IN_APP' | 'EMAIL';

export interface NotificationPreference {
  eventType: NotificationType;
  channel: NotificationChannel;
  enabled: boolean;
}

export interface Notification {
  id: string;
  type: NotificationType;
  referenceId: string;
  message: string;
  read: boolean;
  createdAt: string;
}
