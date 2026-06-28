/**
 * Shared Transloco test helper.
 *
 * Provides TranslocoTestingModule with the full en.json translation file inlined
 * so tests resolve TranslocoService without HTTP requests and assertions work
 * against real English strings.
 *
 * Spec files that use TestBed (2.2 enumeration):
 *   src/app/app.spec.ts  ← already mocks TranslocoService directly; skip
 *   src/app/core/http/auth.interceptor.spec.ts
 *   src/app/core/services/app-preferences.service.spec.ts
 *   src/app/core/services/auth-api.service.spec.ts
 *   src/app/features/ai/ai.service.spec.ts
 *   src/app/features/ai/chat/ai-chat.component.spec.ts
 *   src/app/features/notifications/components/notification-bell/notification-bell.component.spec.ts
 *   src/app/features/notifications/components/notification-panel/notification-panel.component.spec.ts
 *   src/app/features/notifications/services/notification-preferences.service.spec.ts
 *   src/app/features/notifications/services/notification.service.spec.ts
 *   src/app/features/notifications/store/notification-preferences.store.spec.ts
 *   src/app/features/notifications/store/notification.store.spec.ts
 *   src/app/features/projects/project-list/project-list.component.spec.ts
 *   src/app/features/projects/project.service.spec.ts
 *   src/app/features/settings/components/confirm-delete-account-dialog/confirm-delete-account-dialog.component.spec.ts
 *   src/app/features/settings/services/user.service.spec.ts
 *   src/app/features/settings/settings.component.spec.ts
 *   src/app/features/settings/store/profile.store.spec.ts
 *   src/app/features/settings/store/sessions.store.spec.ts
 *   src/app/features/tasks/kanban/kanban-board/kanban-board.component.spec.ts
 *   src/app/features/tasks/kanban/kanban-column/kanban-column.component.spec.ts
 *   src/app/features/tasks/kanban/task-card/task-card.component.spec.ts
 *   src/app/features/tasks/task-form/task-form.component.spec.ts
 *   src/app/features/tasks/task-list/task-list.component.spec.ts
 *   src/app/features/tasks/task.service.spec.ts
 *   src/app/features/tasks/task.store.spec.ts
 *   src/app/features/teams/team-detail/team-detail.component.spec.ts
 *   src/app/features/teams/team.service.spec.ts
 */

import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import en from '../../../public/assets/i18n/en.json';

export function provideTranslocoTesting(options: Partial<TranslocoTestingOptions> = {}): any[] {
  return TranslocoTestingModule.forRoot({
    langs: { en },
    translocoConfig: { defaultLang: 'en' },
    ...options,
  }).providers ?? [];
}
