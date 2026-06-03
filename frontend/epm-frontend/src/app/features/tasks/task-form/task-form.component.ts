import { Component, OnInit, inject, input, signal } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TaskService } from '../task.service';
import { TaskPriority } from '../../../core/models/task.models';

@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './task-form.component.html',
  styleUrl: './task-form.component.scss',
})
export class TaskFormComponent implements OnInit {
  /** When provided, the form is in edit mode */
  taskId = input<string | null>(null);

  private readonly fb = inject(FormBuilder);
  private readonly taskService = inject(TaskService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  projectId = '';
  loading = signal(false);
  error = signal<string | null>(null);

  readonly priorities: TaskPriority[] = ['HIGH', 'MEDIUM', 'LOW'];

  form: FormGroup = this.fb.group({
    title: ['', [Validators.required, Validators.minLength(1)]],
    description: [''],
    priority: ['MEDIUM' as TaskPriority, Validators.required],
    deadline: [null as Date | null],
  });

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') ?? '';
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    const { title, description, priority, deadline } = this.form.value;
    const deadlineStr = deadline ? (deadline as Date).toISOString().split('T')[0] : undefined;

    const id = this.taskId();
    if (id) {
      // Edit mode
      this.taskService
        .update(id, { title, description: description || undefined, priority, deadline: deadlineStr })
        .subscribe({
          next: () => {
            this.loading.set(false);
            this.router.navigate(['..'], { relativeTo: this.route });
          },
          error: () => {
            this.error.set('Failed to update task.');
            this.loading.set(false);
          },
        });
    } else {
      // Create mode
      this.taskService
        .create({
          projectId: this.projectId,
          title,
          description: description || undefined,
          priority,
          deadline: deadlineStr,
        })
        .subscribe({
          next: () => {
            this.loading.set(false);
            this.router.navigate(['..'], { relativeTo: this.route });
          },
          error: () => {
            this.error.set('Failed to create task.');
            this.loading.set(false);
          },
        });
    }
  }

  onCancel(): void {
    this.router.navigate(['..'], { relativeTo: this.route });
  }
}
