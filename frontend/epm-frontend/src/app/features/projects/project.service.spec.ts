import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProjectService } from './project.service';
import { Project, ProjectStatus, ProjectVisibility } from '../../core/models/project.model';
import { CreateProjectRequest } from '../../core/models/create-project.request';
import { provideTranslocoTesting } from '../../testing/transloco-testing';

const BASE_URL = 'http://localhost:8080/api/v1/projects';

const mockProject: Project = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Test Project',
  description: 'A test project',
  status: ProjectStatus.ACTIVE,
  visibility: ProjectVisibility.PRIVATE,
  ownerProfileId: '22222222-2222-2222-2222-222222222222',
  tenantId: '33333333-3333-3333-3333-333333333333',
  createdAt: '2026-06-01T00:00:00Z',
  updatedAt: '2026-06-01T00:00:00Z',
};

describe('ProjectService', () => {
  let service: ProjectService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProjectService,
        provideHttpClient(),
        provideHttpClientTesting(),
        ...provideTranslocoTesting(),
      ],
    });
    service = TestBed.inject(ProjectService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('list()', () => {
    it('should call GET /api/v1/projects', () => {
      const mockProjects: Project[] = [mockProject];

      service.list().subscribe((projects) => {
        expect(projects).toEqual(mockProjects);
      });

      const req = httpMock.expectOne(BASE_URL);
      expect(req.request.method).toBe('GET');
      req.flush(mockProjects);
    });
  });

  describe('create()', () => {
    it('should send POST /api/v1/projects with correct body', () => {
      const createRequest: CreateProjectRequest = {
        name: 'New Project',
        description: 'Description',
        visibility: ProjectVisibility.PRIVATE,
      };

      service.create(createRequest).subscribe((project) => {
        expect(project).toEqual(mockProject);
      });

      const req = httpMock.expectOne(BASE_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(mockProject);
    });
  });

  describe('getById()', () => {
    it('should call GET /api/v1/projects/:id', () => {
      const projectId = '11111111-1111-1111-1111-111111111111';

      service.getById(projectId).subscribe((project) => {
        expect(project).toEqual(mockProject);
      });

      const req = httpMock.expectOne(`${BASE_URL}/${projectId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProject);
    });
  });
});
