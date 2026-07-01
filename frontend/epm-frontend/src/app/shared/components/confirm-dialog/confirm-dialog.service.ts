import {
  Injectable,
  ApplicationRef,
  createComponent,
  EnvironmentInjector,
  inject,
} from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogConfig } from './confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly appRef     = inject(ApplicationRef);
  private readonly injector   = inject(EnvironmentInjector);

  open(config: ConfirmDialogConfig): Observable<boolean> {
    const result$ = new Subject<boolean>();

    const ref = createComponent(ConfirmDialogComponent, {
      environmentInjector: this.injector,
    });

    ref.setInput('config', config);

    const hostEl = (ref.hostView as any).rootNodes[0] as HTMLElement;
    document.body.appendChild(hostEl);
    this.appRef.attachView(ref.hostView);

    ref.instance.confirmed.subscribe((value: boolean) => {
      result$.next(value);
      result$.complete();
      if (hostEl.parentNode === document.body) {
        document.body.removeChild(hostEl);
      }
      this.appRef.detachView(ref.hostView);
      ref.destroy();
    });
    ref.changeDetectorRef.detectChanges();

    return result$.asObservable();
  }
}
