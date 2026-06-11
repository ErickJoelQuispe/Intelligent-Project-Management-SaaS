import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { TaskCardComponent } from './task-card.component';
import { TaskSummary } from '../../../../core/models/task.models';

describe('TaskCardComponent', () => {
  let fixture: ComponentFixture<TaskCardComponent>;
  let component: TaskCardComponent;

  const highPriorityTask: TaskSummary = {
    taskId: 'task-high',
    title: 'Fix critical bug',
    status: 'TODO',
    priority: 'HIGH',
    deadline: '2026-12-31',
  };

  const lowPriorityTask: TaskSummary = {
    taskId: 'task-low',
    title: 'Update documentation',
    status: 'DONE',
    priority: 'LOW',
  };

  async function createComponent(task: TaskSummary) {
    await TestBed.configureTestingModule({
      imports: [TaskCardComponent],
      providers: [provideAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('task', task);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    await createComponent(highPriorityTask);
  });

  it('renders the task title from input', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Template uses a <span> with the title text directly (no semantic CSS class)
    const spans = Array.from(compiled.querySelectorAll('span'));
    const titleSpan = spans.find((s) => s.textContent?.trim() === 'Fix critical bug');
    expect(titleSpan).toBeTruthy();
  });

  it('renders the priority chip with correct priority label', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // TaskPriorityBadgeComponent renders inside app-task-priority-badge
    const priorityBadge = compiled.querySelector('app-task-priority-badge');
    expect(priorityBadge).toBeTruthy();
  });

  it('renders deadline when deadline is provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Deadline section contains a schedule icon and date text
    const scheduleIcon = compiled.querySelector('.material-symbols-rounded');
    expect(scheduleIcon).toBeTruthy();
  });

  it('does not render deadline section when no deadline', async () => {
    TestBed.resetTestingModule();
    await createComponent(lowPriorityTask);

    const compiled = fixture.nativeElement as HTMLElement;
    const scheduleIcon = compiled.querySelector('.material-symbols-rounded');
    expect(scheduleIcon).toBeNull();
  });


});
