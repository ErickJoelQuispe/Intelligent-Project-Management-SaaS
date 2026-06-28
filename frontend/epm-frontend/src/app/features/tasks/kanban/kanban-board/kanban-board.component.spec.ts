import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { KanbanBoardComponent } from './kanban-board.component';
import { TaskStore } from '../../task.store';
import { KanbanColumn } from '../../../../core/models/task.models';

function makeStoreMock(columns: KanbanColumn[]) {
  return {
    loadKanban: vi.fn(),
    kanbanColumns: signal(columns),
    isLoading: signal(false),
    error: signal<string | null>(null),
    kanban: signal({
      TODO: [],
      IN_PROGRESS: [],
      IN_REVIEW: [],
      DONE: [],
      CANCELLED: [],
    }),
  };
}

describe('KanbanBoardComponent', () => {
  let fixture: ComponentFixture<KanbanBoardComponent>;

  async function setup(storeMock: ReturnType<typeof makeStoreMock>) {
    await TestBed.configureTestingModule({
      imports: [KanbanBoardComponent],
      providers: [
        provideRouter([]),
        provideAnimations(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: (_: string) => 'project-abc' } },
          },
        },
      ],
    })
      .overrideComponent(KanbanBoardComponent, {
        set: { providers: [{ provide: TaskStore, useValue: storeMock }] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(KanbanBoardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should render 5 kanban-column elements', async () => {
    const emptyColumns: KanbanColumn[] = [
      { status: 'TODO', tasks: [] },
      { status: 'IN_PROGRESS', tasks: [] },
      { status: 'IN_REVIEW', tasks: [] },
      { status: 'DONE', tasks: [] },
      { status: 'CANCELLED', tasks: [] },
    ];
    const storeMock = makeStoreMock(emptyColumns);
    await setup(storeMock);

    const columns = fixture.nativeElement.querySelectorAll('app-kanban-column');
    expect(columns.length).toBe(5);
    expect(storeMock.loadKanban).toHaveBeenCalledWith('project-abc');
  });

  it('empty state — each column shows placeholder text', async () => {
    const emptyColumns: KanbanColumn[] = [
      { status: 'TODO', tasks: [] },
      { status: 'IN_PROGRESS', tasks: [] },
      { status: 'IN_REVIEW', tasks: [] },
      { status: 'DONE', tasks: [] },
      { status: 'CANCELLED', tasks: [] },
    ];
    const storeMock = makeStoreMock(emptyColumns);
    await setup(storeMock);

    // kanban-column uses an inline .kanban-col-empty div (not app-empty-state component)
    const emptyPlaceholders = fixture.nativeElement.querySelectorAll('.kanban-col-empty');
    expect(emptyPlaceholders.length).toBe(5);
    emptyPlaceholders.forEach((el: Element) => {
      expect(el.textContent).toContain('No tasks');
    });
  });
});
