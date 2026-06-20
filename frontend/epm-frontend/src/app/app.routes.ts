import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'projects',
    pathMatch: 'full',
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./features/settings/settings.component').then(
        (m) => m.SettingsComponent,
      ),
    canActivate: [authGuard],
  },
  {
    // Keep old route working — redirect to new settings page
    path: 'settings/notifications',
    redirectTo: 'settings',
  },
  {
    path: 'projects',
    loadComponent: () =>
      import('./features/projects/project-list/project-list.component').then(
        (m) => m.ProjectListComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/new',
    loadComponent: () =>
      import('./features/projects/project-form/project-form.component').then(
        (m) => m.ProjectFormComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:projectId/edit',
    loadComponent: () =>
      import('./features/projects/project-form/project-form.component').then(
        (m) => m.ProjectFormComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:projectId',
    loadComponent: () =>
      import('./features/projects/project-detail/project-detail.component').then(
        (m) => m.ProjectDetailComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:projectId/tasks/kanban',
    loadComponent: () =>
      import('./features/tasks/kanban/kanban-board/kanban-board.component').then(
        (m) => m.KanbanBoardComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:projectId/tasks/new',
    loadComponent: () =>
      import('./features/tasks/task-form/task-form.component').then(
        (m) => m.TaskFormComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:projectId/tasks/:taskId/edit',
    loadComponent: () =>
      import('./features/tasks/task-form/task-form.component').then(
        (m) => m.TaskFormComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:projectId/tasks',
    loadComponent: () =>
      import('./features/tasks/task-list/task-list.component').then(
        (m) => m.TaskListComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'teams',
    loadComponent: () =>
      import('./features/teams/team-list/team-list.component').then(
        (m) => m.TeamListComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'teams/new',
    loadComponent: () =>
      import('./features/teams/team-create/team-create.component').then(
        (m) => m.TeamCreateComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'teams/:teamId',
    loadComponent: () =>
      import('./features/teams/team-detail/team-detail.component').then(
        (m) => m.TeamDetailComponent,
      ),
    canActivate: [authGuard],
  },
];
