import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'projects',
    pathMatch: 'full',
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
      import('./features/projects/project-create/project-create.component').then(
        (m) => m.ProjectCreateComponent,
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
    path: 'projects/:projectId/tasks',
    loadComponent: () =>
      import('./features/tasks/task-list/task-list.component').then(
        (m) => m.TaskListComponent,
      ),
    canActivate: [authGuard],
  },
];
