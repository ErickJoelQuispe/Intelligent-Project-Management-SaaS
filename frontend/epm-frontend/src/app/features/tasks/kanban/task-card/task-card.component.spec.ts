import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { TaskCardComponent } from './task-card.component';
import { TaskSummary } from '../../../../core/models/task.models';
import { provideTranslocoTesting } from '../../../../testing/transloco-testing';

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
      providers: [provideAnimations(), ...provideTranslocoTesting()],
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
    // Template uses <p class="task-card-title"> to render the task title
    const titleEl = compiled.querySelector('p.task-card-title');
    expect(titleEl).toBeTruthy();
    expect(titleEl?.textContent?.trim()).toBe('Fix critical bug');
  });

  it('renders the priority chip with correct priority label', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // TaskPriorityBadgeComponent renders inside app-task-priority-badge
    const priorityBadge = compiled.querySelector('app-task-priority-badge');
    expect(priorityBadge).toBeTruthy();
  });

  it('renders deadline when deadline is provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Deadline section has a span with text 'schedule'
    const icons = Array.from(compiled.querySelectorAll('.material-symbols-rounded'));
    const scheduleIcon = icons.find((el) => el.textContent?.trim() === 'schedule');
    expect(scheduleIcon).toBeTruthy();
  });

  it('does not render deadline section when no deadline', async () => {
    TestBed.resetTestingModule();
    await createComponent(lowPriorityTask);

    const compiled = fixture.nativeElement as HTMLElement;
    const icons = Array.from(compiled.querySelectorAll('.material-symbols-rounded'));
    const scheduleIcon = icons.find((el) => el.textContent?.trim() === 'schedule');
    expect(scheduleIcon).toBeUndefined();
  });


});
