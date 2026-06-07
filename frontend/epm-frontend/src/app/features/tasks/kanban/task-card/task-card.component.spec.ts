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
    const title = compiled.querySelector('.task-card__title');
    expect(title?.textContent?.trim()).toBe('Fix critical bug');
  });

  it('renders the priority chip with correct priority label', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const priorityChip = compiled.querySelector('.task-card__priority');
    expect(priorityChip?.textContent?.trim()).toBe('HIGH');
  });

  it('renders deadline when deadline is provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const deadline = compiled.querySelector('.task-card__deadline');
    expect(deadline).toBeTruthy();
    expect(deadline?.textContent).toBeTruthy();
  });

  it('does not render deadline section when no deadline', async () => {
    TestBed.resetTestingModule();
    await createComponent(lowPriorityTask);

    const compiled = fixture.nativeElement as HTMLElement;
    const deadline = compiled.querySelector('.task-card__deadline');
    expect(deadline).toBeNull();
  });

  it('priorityColor returns red for HIGH priority', () => {
    const color = component.priorityColor('HIGH');
    expect(color).toContain('priority-high');
  });

  it('priorityColor returns orange for MEDIUM priority', () => {
    const color = component.priorityColor('MEDIUM');
    expect(color).toContain('priority-medium');
  });

  it('priorityColor returns green for LOW priority', () => {
    const color = component.priorityColor('LOW');
    expect(color).toContain('priority-low');
  });
});
