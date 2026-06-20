import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { switchMap, of } from 'rxjs';
import { TaskService } from '../task.service';
import { TaskPriority } from '../../../core/models/task.models';
import { Project } from '../../../core/models/project.model';
import { TenantUser } from '../../../core/models/user-profile.model';
import { UserService } from '../../settings/services/user.service';
import { ProjectService } from '../../projects/project.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';
import { TitleCasePipe } from '@angular/common';

@Component({
  selector: 'app-task-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    ErrorBannerComponent,
    SpinnerComponent,
    TitleCasePipe,
  ],
  templateUrl: './task-form.component.html',
  styleUrl: './task-form.component.scss',
})
export class TaskFormComponent implements OnInit {
  private readonly fb             = inject(FormBuilder);
  private readonly taskService    = inject(TaskService);
  private readonly userService    = inject(UserService);
  private readonly projectService = inject(ProjectService);
  private readonly route          = inject(ActivatedRoute);
  private readonly router         = inject(Router);

  projectId   = '';
  taskId      = signal<string | null>(null);
  loading     = signal(false);
  loadingTask = signal(false);
  error       = signal<string | null>(null);
  users       = signal<TenantUser[]>([]);
  project     = signal<Project | null>(null);

  /** Tracks the assigneeId that was loaded with the task (for change detection). */
  private originalAssigneeId: string | null = null;

  readonly priorities: { value: TaskPriority; label: string; icon: string }[] = [
    { value: 'HIGH',   label: 'High',   icon: 'keyboard_double_arrow_up' },
    { value: 'MEDIUM', label: 'Medium', icon: 'drag_handle' },
    { value: 'LOW',    label: 'Low',    icon: 'keyboard_double_arrow_down' },
  ];

  form = this.fb.nonNullable.group({
    title:       ['', [Validators.required, Validators.minLength(1)]],
    description: [''],
    priority:    ['MEDIUM' as TaskPriority, Validators.required],
    deadline:    [''],
    assigneeId:  [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') ?? '';
    const id = this.route.snapshot.paramMap.get('taskId');

    this.userService.listTenantUsers().subscribe({
      next: (u) => this.users.set(u),
      error: () => {},
    });

    if (this.projectId) {
      this.projectService.getById(this.projectId).subscribe({
        next: (p) => this.project.set(p),
        error: () => {},
      });
    }

    if (id) {
      this.taskId.set(id);
      this.loadTask(id);
    }
  }

  private loadTask(id: string): void {
    if (!id || id === 'undefined' || id === 'null') {
      this.error.set('Invalid task ID.');
      return;
    }
    this.loadingTask.set(true);
    this.taskService.getById(id).subscribe({
      next: (task) => {
        this.originalAssigneeId = task.assigneeId ?? null;
        this.form.patchValue({
          title:       task.title,
          description: task.description ?? '',
          priority:    task.priority,
          deadline:    task.deadline ?? '',
          assigneeId:  task.assigneeId ?? '',
        });
        this.loadingTask.set(false);
      },
      error: () => {
        this.error.set('Failed to load task.');
        this.loadingTask.set(false);
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.loading.set(true);
    this.error.set(null);
    const { title, description, priority, deadline, assigneeId } = this.form.getRawValue();

    const id = this.taskId();
    if (id) {
      const currentAssigneeId = assigneeId || null;
      const assigneeChanged   = currentAssigneeId !== this.originalAssigneeId;

      this.taskService
        .update(id, {
          title,
          description: description || undefined,
          priority,
          deadline: deadline || undefined,
        })
        .pipe(
          switchMap(() => {
            if (assigneeChanged) {
              return this.taskService.assign(id, assigneeId || null);
            }
            return of(null);
          }),
        )
        .subscribe({
          next:  () => { this.loading.set(false); this.goBack(); },
          error: () => { this.error.set('Failed to update task.'); this.loading.set(false); },
        });
    } else {
      this.taskService
        .create({
          projectId: this.projectId,
          title,
          description: description || undefined,
          priority,
          deadline: deadline || undefined,
          assigneeId: assigneeId || undefined,
        })
        .subscribe({
          next:  () => { this.loading.set(false); this.goBack(); },
          error: () => { this.error.set('Failed to create task.'); this.loading.set(false); },
        });
    }
  }

  onCancel(): void {
    this.goBack();
  }

  assigneeName(id: string): string {
    const user = this.users().find(u => u.id === id);
    if (!user) return id;
    return (user.firstName && user.lastName)
      ? `${user.firstName} ${user.lastName}`
      : user.email;
  }

  private goBack(): void {
    this.router.navigate(['/projects', this.projectId, 'tasks']);
  }
}
