import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { TaskFormComponent } from './task-form.component';
import { TaskService } from '../task.service';
import { provideTranslocoTesting } from '../../../testing/transloco-testing';

describe('TaskFormComponent', () => {
  let component: TaskFormComponent;
  let fixture: ComponentFixture<TaskFormComponent>;
  let taskServiceMock: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    taskServiceMock = {
      create: vi.fn().mockReturnValue(of({})),
      update: vi.fn().mockReturnValue(of({})),
      getById: vi.fn().mockReturnValue(of({})),
    };

    await TestBed.configureTestingModule({
      imports: [TaskFormComponent],
      providers: [
        provideRouter([]),
        provideAnimations(),
        {
          provide: ActivatedRoute,
          useValue: {
            // projectId param returns 'project-abc', taskId returns null (create mode)
            snapshot: { paramMap: { get: (key: string) => key === 'projectId' ? 'project-abc' : null } },
          },
        },
        { provide: TaskService, useValue: taskServiceMock },
        ...provideTranslocoTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('submit with empty title → validation error visible in DOM', async () => {
    // Clear the title field and touch it
    component.form.get('title')!.setValue('');
    component.form.get('title')!.markAsTouched();

    // Trigger submit
    const formEl = fixture.nativeElement.querySelector('form');
    formEl.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // Template uses a <span class="tf-error"> when title is touched + invalid
    const errorSpan = fixture.nativeElement.querySelector('.tf-error');
    expect(errorSpan).not.toBeNull();
    expect(errorSpan.textContent).toContain('required');
  });

  it('should render create mode heading when no taskId', () => {
    fixture.detectChanges();
    // Title is rendered inside app-page-header as an h1 element
    const heading = fixture.nativeElement.querySelector('h1');
    expect(heading.textContent).toContain('New task');
  });
});
