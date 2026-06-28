import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { ProjectService } from '../project.service';
import { ProjectVisibility } from '../../../core/models/project.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';

@Component({
  selector: 'app-project-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    PageHeaderComponent,
    ButtonComponent,
    ErrorBannerComponent,
    SpinnerComponent,
  ],
  templateUrl: './project-form.component.html',
})
export class ProjectFormComponent implements OnInit {
  private readonly fb               = inject(FormBuilder);
  private readonly projectService   = inject(ProjectService);
  private readonly router           = inject(Router);
  private readonly route            = inject(ActivatedRoute);
  private readonly translocoService = inject(TranslocoService);

  projectId   = signal<string | null>(null);
  isEditMode  = signal(false);
  loading     = signal(false);
  submitting  = signal(false);
  error       = signal<string | null>(null);

  readonly visibilityOptions = [
    { value: ProjectVisibility.PRIVATE, label: 'projects.form.visibility.private' },
    { value: ProjectVisibility.TEAM,    label: 'projects.form.visibility.team' },
    { value: ProjectVisibility.PUBLIC,  label: 'projects.form.visibility.public' },
  ];

  form = this.fb.nonNullable.group({
    name:        ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
    visibility:  [ProjectVisibility.PRIVATE, Validators.required],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('projectId');
    if (id) {
      this.projectId.set(id);
      this.isEditMode.set(true);
      this.loadProject(id);
    }
  }

  private loadProject(id: string): void {
    this.loading.set(true);
    this.projectService.getById(id).subscribe({
      next: (p) => {
        this.form.patchValue({
          name:        p.name,
          description: p.description ?? '',
          visibility:  p.visibility,
        });
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.translocoService.translate('projects.form.loadError'));
        this.loading.set(false);
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.submitting.set(true);
    this.error.set(null);
    const { name, description, visibility } = this.form.getRawValue();

    const id = this.projectId();
    if (id && this.isEditMode()) {
      this.projectService
        .update(id, { name, description: description || undefined, visibility })
        .subscribe({
          next:  (p) => { this.submitting.set(false); this.router.navigate(['/projects', p.id]); },
          error: ()  => { this.error.set(this.translocoService.translate('projects.form.updateError')); this.submitting.set(false); },
        });
    } else {
      this.projectService
        .create({ name, description: description || undefined, visibility })
        .subscribe({
          next:  () => { this.submitting.set(false); this.router.navigate(['/projects']); },
          error: () => { this.error.set(this.translocoService.translate('projects.form.createError')); this.submitting.set(false); },
        });
    }
  }

  onCancel(): void {
    const id = this.projectId();
    if (id && this.isEditMode()) {
      this.router.navigate(['/projects', id]);
    } else {
      this.router.navigate(['/projects']);
    }
  }
}
