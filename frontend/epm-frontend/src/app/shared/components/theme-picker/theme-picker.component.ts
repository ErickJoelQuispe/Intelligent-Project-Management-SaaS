import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  Input,
  HostListener,
  ElementRef,
  OnDestroy,
  ApplicationRef,
  createComponent,
  EnvironmentInjector,
  ComponentRef,
  DOCUMENT,
} from '@angular/core';
import { ThemeService, Theme } from '../../../core/theme/theme.service';
import { ThemeDrawerComponent } from './theme-drawer.component';

@Component({
  selector: 'app-theme-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <!-- Trigger button — reuses sidebar ctrl-btn visual language -->
    <button
      (click)="openDrawer()"
      class="ctrl-btn"
      [class.ctrl-btn--icon-only]="collapsed"
      title="Themes"
      aria-label="Open theme picker"
    >
      <span class="material-symbols-rounded ctrl-btn-icon" aria-hidden="true">palette</span>
      @if (!collapsed) {
        <span class="ctrl-btn-label">Themes</span>
      }
    </button>

    <style>
      :host { display: contents; }

      .ctrl-btn {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        border-radius: 0.5rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-text-muted);
        cursor: pointer;
        transition: color 0.15s ease, background 0.15s ease;
        background: transparent;
        border: none;
        flex: 1;
        min-width: 0;
        outline-offset: 2px;
      }
      .ctrl-btn--icon-only {
        flex: none;
        width: 2.375rem;
        height: 2.375rem;
        justify-content: center;
        padding: 0;
      }
      .ctrl-btn:hover {
        color: var(--color-text-primary);
        background: color-mix(in oklch, var(--color-bg-overlay) 70%, transparent);
      }
      .ctrl-btn:focus-visible {
        outline: 2px solid var(--color-accent);
      }
      .ctrl-btn-icon  { font-size: 1.125rem; flex-shrink: 0; }
      .ctrl-btn-label {
        flex: 1; min-width: 0;
        overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        text-align: left;
      }
    </style>
  `,
})
export class ThemePickerComponent implements OnDestroy {
  @Input() collapsed = false;

  private readonly appRef        = inject(ApplicationRef);
  private readonly envInjector   = inject(EnvironmentInjector);
  private readonly document      = inject(DOCUMENT);

  private drawerRef: ComponentRef<ThemeDrawerComponent> | null = null;

  openDrawer(): void {
    if (this.drawerRef) return; // already open

    const ref = createComponent(ThemeDrawerComponent, {
      environmentInjector: this.envInjector,
    });

    ref.instance.close.subscribe(() => this.destroyDrawer());

    this.appRef.attachView(ref.hostView);
    this.document.body.appendChild(ref.location.nativeElement);
    this.drawerRef = ref;
  }

  private destroyDrawer(): void {
    if (!this.drawerRef) return;
    this.appRef.detachView(this.drawerRef.hostView);
    this.drawerRef.destroy();
    this.drawerRef = null;
  }

  ngOnDestroy(): void {
    this.destroyDrawer();
  }
}
