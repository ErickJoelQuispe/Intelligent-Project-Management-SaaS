import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { TeamService } from '../team.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner/error-banner.component';

@Component({
  selector: 'app-team-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    PageHeaderComponent,
    ButtonComponent,
    ErrorBannerComponent,
  ],
  templateUrl: './team-create.component.html',
})
export class TeamCreateComponent {
  private readonly fb               = inject(FormBuilder);
  private readonly teamService      = inject(TeamService);
  private readonly router           = inject(Router);
  private readonly translocoService = inject(TranslocoService);

  submitting = signal(false);
  error      = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    name:        ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(500)],
  });

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.submitting.set(true);
    this.error.set(null);
    const { name, description } = this.form.getRawValue();

    this.teamService
      .create({ name, description: description || undefined })
      .subscribe({
        next:  (t) => { this.submitting.set(false); this.router.navigate(['/teams', t.id]); },
        error: ()  => { this.error.set(this.translocoService.translate('teams.create.createError')); this.submitting.set(false); },
      });
  }

  onCancel(): void {
    this.router.navigate(['/teams']);
  }
}
