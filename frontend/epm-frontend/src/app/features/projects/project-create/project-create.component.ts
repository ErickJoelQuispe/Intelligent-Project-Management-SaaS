import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProjectService } from '../project.service';
import { ProjectVisibility } from '../../../core/models/project.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';

@Component({
  selector: 'app-project-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    ErrorBannerComponent,
  ],
  templateUrl: './project-create.component.html',
  styleUrl: './project-create.component.scss',
})
export class ProjectCreateComponent {
  private readonly fb             = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly router         = inject(Router);

  submitting = signal(false);
  error      = signal<string | null>(null);

  readonly visibilityOptions = [
    { value: ProjectVisibility.PRIVATE, label: 'Private — only you' },
    { value: ProjectVisibility.TEAM,    label: 'Team — members only' },
    { value: ProjectVisibility.PUBLIC,  label: 'Public — everyone' },
  ];

  form = this.fb.nonNullable.group({
    name:        ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
    visibility:  [ProjectVisibility.PRIVATE, Validators.required],
  });

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.submitting.set(true);
    this.error.set(null);
    const { name, description, visibility } = this.form.getRawValue();

    this.projectService
      .create({ name, description: description || undefined, visibility })
      .subscribe({
        next:  () => { this.submitting.set(false); this.router.navigate(['/projects']); },
        error: () => { this.error.set('Failed to create project. Please try again.'); this.submitting.set(false); },
      });
  }

  onCancel(): void {
    this.router.navigate(['/projects']);
  }
}
