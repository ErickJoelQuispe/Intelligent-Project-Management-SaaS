import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ProjectCreateComponent } from './project-create.component';
import { ProjectService } from '../project.service';
import { Project, ProjectStatus, ProjectVisibility } from '../../../core/models/project.model';

const mockProject: Project = {
  id: '1',
  name: 'New Project',
  status: ProjectStatus.ACTIVE,
  visibility: ProjectVisibility.PRIVATE,
  ownerProfileId: 'owner-1',
  tenantId: 'tenant-1',
  createdAt: '2026-06-01T00:00:00Z',
  updatedAt: '2026-06-01T00:00:00Z',
};

describe('ProjectCreateComponent', () => {
  let component: ProjectCreateComponent;
  let fixture: ComponentFixture<ProjectCreateComponent>;
  let projectServiceMock: { create: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    projectServiceMock = {
      create: vi.fn().mockReturnValue(of(mockProject)),
    };

    await TestBed.configureTestingModule({
      imports: [ProjectCreateComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceMock },
        provideRouter([]),
        provideAnimations(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectCreateComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not call API when name is empty and form is invalid', async () => {
    // Form starts with empty name (invalid)
    expect(component.form.invalid).toBe(true);

    component.onSubmit();
    await fixture.whenStable();

    expect(projectServiceMock.create).not.toHaveBeenCalled();
  });

  it('should call API and navigate to /projects on valid form submission', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.controls.name.setValue('My Valid Project');
    component.form.controls.visibility.setValue(ProjectVisibility.PRIVATE);
    fixture.detectChanges();

    expect(component.form.valid).toBe(true);

    component.onSubmit();
    await fixture.whenStable();

    expect(projectServiceMock.create).toHaveBeenCalledWith({
      name: 'My Valid Project',
      description: undefined,
      visibility: ProjectVisibility.PRIVATE,
    });
    expect(navigateSpy).toHaveBeenCalledWith(['/projects']);
  });

  it('should show snackbar on API error and not navigate', async () => {
    projectServiceMock.create.mockReturnValue(throwError(() => new Error('API error')));
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.controls.name.setValue('My Project');
    component.onSubmit();
    await fixture.whenStable();

    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
