import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProjectService } from '../project.service';
import { ProjectVisibility } from '../../../core/models/project.model';

@Component({
  selector: 'app-project-create',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './project-create.component.html',
  styleUrl: './project-create.component.scss',
})
export class ProjectCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  visibilityOptions = [
    { value: ProjectVisibility.PRIVATE, label: 'Private' },
    { value: ProjectVisibility.TEAM, label: 'Team' },
    { value: ProjectVisibility.PUBLIC, label: 'Public' },
  ];

  submitting = false;

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
    visibility: [ProjectVisibility.PRIVATE, Validators.required],
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    const { name, description, visibility } = this.form.getRawValue();

    this.projectService
      .create({ name, description: description || undefined, visibility })
      .subscribe({
        next: () => {
          this.submitting = false;
          this.router.navigate(['/projects']);
        },
        error: () => {
          this.submitting = false;
          this.snackBar.open('Failed to create project. Please try again.', 'Dismiss', {
            duration: 5000,
          });
        },
      });
  }

  onCancel(): void {
    this.router.navigate(['/projects']);
  }
}
