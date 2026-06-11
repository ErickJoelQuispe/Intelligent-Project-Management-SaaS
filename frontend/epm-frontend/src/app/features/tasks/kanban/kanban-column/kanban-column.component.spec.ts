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
    // Status label rendered as an uppercase span inside the column header
    const spans = Array.from(compiled.querySelectorAll('span'));
    const header = spans.find(s => s.textContent?.trim().toUpperCase() === 'TO DO');
    expect(header?.textContent?.trim().toUpperCase()).toBe('TO DO');
  });

  it('displays correct task count matching number of tasks provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Task count rendered inside app-badge
    const badge = compiled.querySelector('app-badge');
    expect(badge?.textContent?.trim()).toBe('2');
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
    const emptyState = compiled.querySelector('app-empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent?.trim()).toContain('No tasks');
  });
});
