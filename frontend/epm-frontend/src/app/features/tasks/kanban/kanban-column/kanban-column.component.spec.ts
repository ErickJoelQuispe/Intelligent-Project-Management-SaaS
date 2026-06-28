import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { KanbanColumnComponent } from './kanban-column.component';
import { TaskSummary } from '../../../../core/models/task.models';

describe('KanbanColumnComponent', () => {
  let fixture: ComponentFixture<KanbanColumnComponent>;
  let component: KanbanColumnComponent;

  const mockTasks: TaskSummary[] = [
    { taskId: 'task-1', title: 'Implement login', status: 'TODO', priority: 'HIGH' },
    { taskId: 'task-2', title: 'Write tests', status: 'TODO', priority: 'MEDIUM' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KanbanColumnComponent],
      providers: [
        provideAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(KanbanColumnComponent);
    component = fixture.componentInstance;
    // projectId is required by KanbanColumnComponent (used by task-card children)
    fixture.componentRef.setInput('projectId', 'proj-test');
    fixture.componentRef.setInput('status', 'TODO');
    fixture.componentRef.setInput('tasks', mockTasks);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders the human-readable column title for the given status', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Status label rendered in an <h2 class="kanban-col-title"> element
    const h2 = compiled.querySelector('h2.kanban-col-title');
    expect(h2?.textContent?.trim().toUpperCase()).toBe('TO DO');
  });

  it('displays correct task count matching number of tasks provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Task count rendered in <span class="kanban-col-count">
    const countSpan = compiled.querySelector('.kanban-col-count');
    expect(countSpan?.textContent?.trim()).toBe('2');
  });

  it('renders a task card for each task in the tasks input', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const taskCards = compiled.querySelectorAll('app-task-card');
    expect(taskCards.length).toBe(2);
  });

  it('shows empty state when tasks array is empty', () => {
    fixture.componentRef.setInput('tasks', []);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    // The column uses an inline .kanban-col-empty div (not app-empty-state component)
    const emptyState = compiled.querySelector('.kanban-col-empty');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent?.trim()).toContain('No tasks');
  });
});
