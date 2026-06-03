import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { TaskStore } from './task.store';
import { TaskService } from './task.service';
import { KanbanResponse } from '../../core/models/task.models';

describe('TaskStore', () => {
  let store: InstanceType<typeof TaskStore>;
  let taskServiceMock: { getKanban: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    taskServiceMock = {
      getKanban: vi.fn(),
      create: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TaskStore,
        { provide: TaskService, useValue: taskServiceMock },
      ],
    });

    store = TestBed.inject(TaskStore);
  });

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  it('loadKanban() updates kanban signal with mocked response', () => {
    const mockResponse: KanbanResponse = {
      columns: {
        TODO: [{ taskId: 't1', title: 'Task One', status: 'TODO', priority: 'HIGH' }],
        IN_PROGRESS: [{ taskId: 't2', title: 'Task Two', status: 'IN_PROGRESS', priority: 'MEDIUM' }],
        IN_REVIEW: [],
        DONE: [],
        CANCELLED: [],
      },
    };
    taskServiceMock.getKanban.mockReturnValue(of(mockResponse));

    TestBed.runInInjectionContext(() => {
      store.loadKanban('project-123');
    });

    expect(taskServiceMock.getKanban).toHaveBeenCalledWith('project-123');
    expect(store.kanban()['TODO'].length).toBe(1);
    expect(store.kanban()['IN_PROGRESS'].length).toBe(1);
    expect(store.kanban()['TODO'][0].title).toBe('Task One');
  });

  it('kanbanColumns() returns ordered array of 5 columns', () => {
    const columns = store.kanbanColumns();
    expect(columns.length).toBe(5);
    expect(columns[0].status).toBe('TODO');
    expect(columns[1].status).toBe('IN_PROGRESS');
    expect(columns[2].status).toBe('IN_REVIEW');
    expect(columns[3].status).toBe('DONE');
    expect(columns[4].status).toBe('CANCELLED');
  });
});
