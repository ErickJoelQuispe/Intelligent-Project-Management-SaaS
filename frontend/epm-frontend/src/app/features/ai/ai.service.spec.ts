import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AiService } from './ai.service';

const BASE_URL = 'http://localhost:8080/api/v1/ai';

describe('AiService', () => {
  let service: AiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('generates tasks via POST', () => {
    const expected = {
      tasks: [{ title: 'Task 1', description: 'Desc', priority: 'HIGH' as const }],
      cached: false,
    };

    service.generateTasks('proj-1', 'Build a login').subscribe((res) => {
      expect(res.tasks.length).toBe(1);
      expect(res.cached).toBe(false);
    });

    const req = httpMock.expectOne(`${BASE_URL}/tasks/generate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ projectId: 'proj-1', description: 'Build a login' });
    req.flush(expected);
  });

  it('gets project summary via POST', () => {
    const expected = { summary: 'Project is on track', cached: true };

    service.getProjectSummary('proj-1').subscribe((res) => {
      expect(res.summary).toBe('Project is on track');
      expect(res.cached).toBe(true);
    });

    const req = httpMock.expectOne(`${BASE_URL}/projects/proj-1/summary`);
    expect(req.request.method).toBe('POST');
    req.flush(expected);
  });
});
