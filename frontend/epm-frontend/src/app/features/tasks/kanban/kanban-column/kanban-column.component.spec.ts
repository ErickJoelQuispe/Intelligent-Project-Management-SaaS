import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
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
      providers: [provideAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(KanbanColumnComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('status', 'TODO');
    fixture.componentRef.setInput('tasks', mockTasks);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders the human-readable column title for the given status', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('.kanban-column__status-label');
    expect(header?.textContent?.trim()).toBe('To Do');
  });

  it('displays correct task count matching number of tasks provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const count = compiled.querySelector('.kanban-column__count');
    expect(count?.textContent?.trim()).toBe('2');
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
    const emptyState = compiled.querySelector('.kanban-column__empty');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent?.trim()).toContain('No tasks');
  });
});
