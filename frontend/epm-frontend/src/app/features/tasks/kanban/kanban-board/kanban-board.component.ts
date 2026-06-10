import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TaskStore } from '../../task.store';
import { KanbanColumnComponent } from '../kanban-column/kanban-column.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';

@Component({
  selector: 'app-kanban-board',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    KanbanColumnComponent,
    PageHeaderComponent,
    ButtonComponent,
    SpinnerComponent,
    ErrorBannerComponent,
  ],
  templateUrl: './kanban-board.component.html',
  styleUrl: './kanban-board.component.scss',
  providers: [TaskStore],
})
export class KanbanBoardComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  readonly store = inject(TaskStore);

  projectId = '';

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') ?? '';
    this.store.loadKanban(this.projectId);
  }
}
