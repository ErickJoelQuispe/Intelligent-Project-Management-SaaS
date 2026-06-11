import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TaskListComponent } from './task-list.component';
import { TaskService } from '../task.service';
import { TaskSummary } from '../../../core/models/task.models';

describe('TaskListComponent', () => {
  let fixture: ComponentFixture<TaskListComponent>;
  let component: TaskListComponent;
  let taskServiceMock: { list: ReturnType<typeof vi.fn> };

  const mockTasks: TaskSummary[] = [
    { taskId: 't1', title: 'Fix login bug', status: 'TODO', priority: 'HIGH' },
    { taskId: 't2', title: 'Write unit tests', status: 'IN_PROGRESS', priority: 'MEDIUM' },
  ];

  beforeEach(async () => {
    taskServiceMock = {
      list: vi.fn().mockReturnValue(
        of({ content: mockTasks, totalElements: 2, totalPages: 1, size: 10, number: 0 }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [TaskListComponent],
      providers: [
        provideRouter([]),
        provideAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TaskService, useValue: taskServiceMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'project-123' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders task titles from service response', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Template renders task titles in <span> elements inside custom grid rows
    expect(compiled.textContent).toContain('Fix login bug');
    expect(compiled.textContent).toContain('Write unit tests');
  });

  it('shows error message when task loading fails', async () => {
    taskServiceMock.list.mockReturnValue(throwError(() => new Error('Network error')));
    component.loadTasks();
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    // Template uses app-error-banner (not .error-message CSS class)
    const errorEl = compiled.querySelector('app-error-banner');
    expect(errorEl).toBeTruthy();
    expect(errorEl?.textContent?.trim()).toContain('Failed to load tasks');
  });

  it('calls service with correct project ID and pagination', () => {
    expect(taskServiceMock.list).toHaveBeenCalledWith('project-123', 0, 10);
  });
});
