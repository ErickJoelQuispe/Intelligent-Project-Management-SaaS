import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ProjectListComponent } from './project-list.component';
import { ProjectService } from '../project.service';
import { Project, ProjectStatus, ProjectVisibility } from '../../../core/models/project.model';

const mockProjects: Project[] = [
  {
    id: '1',
    name: 'Alpha',
    description: 'First project',
    status: ProjectStatus.ACTIVE,
    visibility: ProjectVisibility.PRIVATE,
    ownerProfileId: 'owner-1',
    tenantId: 'tenant-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: '2',
    name: 'Beta',
    status: ProjectStatus.ARCHIVED,
    visibility: ProjectVisibility.TEAM,
    ownerProfileId: 'owner-1',
    tenantId: 'tenant-1',
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-01T00:00:00Z',
  },
  {
    id: '3',
    name: 'Gamma',
    status: ProjectStatus.COMPLETED,
    visibility: ProjectVisibility.PUBLIC,
    ownerProfileId: 'owner-2',
    tenantId: 'tenant-1',
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
  },
];

describe('ProjectListComponent', () => {
  let component: ProjectListComponent;
  let fixture: ComponentFixture<ProjectListComponent>;
  let projectServiceMock: { list: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    projectServiceMock = {
      list: vi.fn().mockReturnValue(of([])),
    };

    await TestBed.configureTestingModule({
      imports: [ProjectListComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceMock },
        provideRouter([]),
        provideAnimations(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render 3 project cards with mock data', async () => {
    projectServiceMock.list.mockReturnValue(of(mockProjects));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('app-project-card');
    expect(cards.length).toBe(3);
  });

  it('should show empty state when project list is empty', async () => {
    projectServiceMock.list.mockReturnValue(of([]));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector('app-empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState.textContent).toContain('No projects yet');
  });

  // W-05: error display assertion — DOM element must be visible, not just absence of navigation
  it('should display error banner when API returns error (W-05)', async () => {
    projectServiceMock.list.mockReturnValue(
      throwError(() => ({ status: 500, statusText: 'Internal Server Error' })),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('app-error-banner');
    expect(errorEl).not.toBeNull();
    expect(errorEl.textContent).toContain('Failed to load projects');
  });
});
