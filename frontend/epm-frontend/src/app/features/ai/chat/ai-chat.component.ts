import {
  Component,
  ChangeDetectionStrategy,
  inject,
  input,
  signal,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { AiService } from '../ai.service';
import { ChatMessage } from '../models/chat.models';
import { ButtonComponent } from '../../../shared/components/button/button.component';

@Component({
  selector: 'app-ai-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, FormsModule],
  templateUrl: './ai-chat.component.html',
})
export class AiChatComponent {
  /** Optional project scope for all chat requests. */
  projectId = input<string | undefined>(undefined);

  readonly messages  = signal<ChatMessage[]>([]);
  readonly loading   = signal(false);
  readonly inputText = signal('');

  private readonly aiService  = inject(AiService);
  private readonly destroyRef = inject(DestroyRef);

  private abortController: AbortController | null = null;

  constructor() {
    // Abort any active stream when the component is destroyed
    this.destroyRef.onDestroy(() => {
      this.abortActiveStream();
    });
  }

  send(): void {
    const text = this.inputText().trim();
    if (!text) return;

    // Abort any prior active stream
    this.abortActiveStream();

    // Create a new AbortController for this request
    this.abortController = new AbortController();

    // Append user message immediately
    this.messages.update((prev) => [
      ...prev,
      { role: 'user', content: text },
    ]);

    // Append streaming placeholder for assistant
    this.messages.update((prev) => [
      ...prev,
      { role: 'assistant', content: '', streaming: true },
    ]);

    // Clear input
    this.inputText.set('');

    // Set loading
    this.loading.set(true);

    // Subscribe to the stream
    this.aiService
      .streamChat(text, this.projectId() ?? undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (token) => {
          this.messages.update((prev) => {
            const updated = [...prev];
            const lastIdx = updated.length - 1;
            if (lastIdx >= 0 && updated[lastIdx].role === 'assistant') {
              updated[lastIdx] = {
                ...updated[lastIdx],
                content: updated[lastIdx].content + token,
              };
            }
            return updated;
          });
        },
        complete: () => {
          // Mark the last assistant message as no longer streaming
          this.messages.update((prev) => {
            const updated = [...prev];
            const lastIdx = updated.length - 1;
            if (lastIdx >= 0 && updated[lastIdx].role === 'assistant') {
              updated[lastIdx] = { ...updated[lastIdx], streaming: false };
            }
            return updated;
          });
          this.loading.set(false);
          this.abortController = null;
        },
        error: () => {
          // Mark streaming bubble as done and append error message
          this.messages.update((prev) => {
            const updated = [...prev];
            const lastIdx = updated.length - 1;
            if (lastIdx >= 0 && updated[lastIdx].role === 'assistant') {
              updated[lastIdx] = { ...updated[lastIdx], streaming: false };
            }
            return [
              ...updated,
              { role: 'error', content: 'Something went wrong. Please try again.' },
            ];
          });
          this.loading.set(false);
          this.abortController = null;
        },
      });
  }

  onEnterKey(event: KeyboardEvent): void {
    if (!event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  private abortActiveStream(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
  }
}
