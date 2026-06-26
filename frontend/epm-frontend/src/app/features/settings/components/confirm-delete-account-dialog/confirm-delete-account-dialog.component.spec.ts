import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDeleteAccountDialogComponent } from './confirm-delete-account-dialog.component';

describe('ConfirmDeleteAccountDialogComponent', () => {
  let mockDialogRef: { close: ReturnType<typeof vi.fn> };

  function setup() {
    mockDialogRef = { close: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ConfirmDeleteAccountDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: {} },
      ],
    });

    const fixture = TestBed.createComponent(ConfirmDeleteAccountDialogComponent);
    fixture.detectChanges();
    return { fixture, compiled: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => vi.restoreAllMocks());

  // ── RED: confirm() closes dialog with true ────────────────────────────────

  it('confirm() calls dialogRef.close(true)', () => {
    const { fixture } = setup();
    const component = fixture.componentInstance;

    component.confirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith(true);
  });

  // ── TRIANGULATE: cancel() closes dialog with false ────────────────────────

  it('cancel() calls dialogRef.close(false)', () => {
    const { fixture } = setup();
    const component = fixture.componentInstance;

    component.cancel();

    expect(mockDialogRef.close).toHaveBeenCalledWith(false);
  });

  // ── Dialog renders the irreversible warning text ──────────────────────────

  it('renders warning text about irreversible action', () => {
    const { compiled } = setup();

    expect(compiled.textContent).toContain('irreversible');
  });
});
