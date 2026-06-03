import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TaskService } from './task.service';
import { KanbanResponse } from '../../core/models/task.models';

describe('TaskService', () => {
  let service: TaskService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TaskService,
      ],
    });

    service = TestBed.inject(TaskService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getKanban() calls correct URL and returns mapped response', () => {
    const mockResponse: KanbanResponse = {
      columns: {
        TODO: [{ taskId: 't1', title: 'Task 1', status: 'TODO', priority: 'HIGH' }],
        IN_PROGRESS: [],
        IN_REVIEW: [],
        DONE: [],
        CANCELLED: [],
      },
    };

    let result: KanbanResponse | undefined;
    service.getKanban('project-123').subscribe((r) => (result = r));

    const req = httpMock.expectOne(
      'http://localhost:8080/api/v1/projects/project-123/tasks/kanban',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(result).toEqual(mockResponse);
    expect(result?.columns['TODO'].length).toBe(1);
  });

  it('list() calls correct URL with pagination params', () => {
    service.list('project-123', 0, 10).subscribe();

    const req = httpMock.expectOne(
      (r) =>
        r.url === 'http://localhost:8080/api/v1/projects/project-123/tasks' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });
});
