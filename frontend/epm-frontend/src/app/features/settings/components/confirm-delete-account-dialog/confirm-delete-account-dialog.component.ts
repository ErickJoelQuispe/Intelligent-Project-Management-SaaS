import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';

/**
 * Confirmation dialog for the account deletion flow.
 *
 * Returns `true` when the user confirms deletion, `false` on cancel.
 */
@Component({
  selector: 'app-confirm-delete-account-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule],
  template: `
    <div style="padding: 1.5rem; max-width: 400px;">
      <h2 style="margin: 0 0 0.75rem; font-size: 1.1rem; font-weight: 600; color: var(--color-danger, #dc2626);">
        Delete account
      </h2>

      <p style="margin: 0 0 1.5rem; font-size: 0.875rem; line-height: 1.5; color: var(--color-text-secondary, #6b7280);">
        This action is <strong>irreversible</strong>. Your account will be deactivated
        and all associated data will be permanently removed. This cannot be undone.
      </p>

      <div style="display: flex; justify-content: flex-end; gap: 0.75rem;">
        <button
          (click)="cancel()"
          style="padding: 0.5rem 1rem; border-radius: 0.5rem; border: 1px solid #d1d5db;
                 background: transparent; cursor: pointer; font-size: 0.875rem; font-weight: 500;"
        >
          Cancel
        </button>
        <button
          (click)="confirm()"
          style="padding: 0.5rem 1rem; border-radius: 0.5rem; border: none;
                 background: #dc2626; color: #fff; cursor: pointer;
                 font-size: 0.875rem; font-weight: 500;"
        >
          Delete my account
        </button>
      </div>
    </div>
  `,
})
export class ConfirmDeleteAccountDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ConfirmDeleteAccountDialogComponent>);

  confirm(): void {
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
