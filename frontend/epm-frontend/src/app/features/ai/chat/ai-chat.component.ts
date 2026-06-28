import {
  Component,
  ChangeDetectionStrategy,
  inject,
  input,
  signal,
  effect,
  viewChild,
  ElementRef,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AiService } from '../ai.service';
import { ChatMessage, ChatTurn } from '../models/chat.models';


@Component({
  selector: 'app-ai-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './ai-chat.component.html',
})
export class AiChatComponent {
  /** Optional project scope for all chat requests. */
  projectId = input<string | undefined>(undefined);

  /** Optional task list for AI context enrichment. */
  projectTasks = input<{ title: string; status: string }[]>([]);

  readonly messages  = signal<ChatMessage[]>([]);
  readonly loading   = signal(false);
  readonly inputText = signal('');

  private readonly aiService  = inject(AiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sanitizer  = inject(DomSanitizer);

  private abortController: AbortController | null = null;

  /** Reference to the scrollable message list container. */
  private readonly messageList = viewChild<ElementRef<HTMLDivElement>>('messageList');

  constructor() {
    // Abort any active stream when the component is destroyed
    this.destroyRef.onDestroy(() => {
      this.abortActiveStream();
    });

    // Auto-scroll to bottom whenever messages change
    effect(() => {
      const msgs = this.messages();
      if (msgs.length === 0) return;
      const el = this.messageList()?.nativeElement;
      if (el) {
        setTimeout(() => {
          el.scrollTop = el.scrollHeight;
        }, 0);
      }
    });
  }

  send(): void {
    const text = this.inputText().trim();
    if (!text) return;

    // Abort any prior active stream
    this.abortActiveStream();

    // Create a new AbortController for this request
    this.abortController = new AbortController();

    // Build history before mutating messages (excludes the msgs we're about to add)
    const history = this.buildHistory();

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
      .streamChat(text, this.projectId() ?? undefined, history, this.projectTasks())
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

  /** Auto-resize the textarea as the user types. Resets height first so shrinking works. */
  onInput(el: HTMLTextAreaElement): void {
    this.inputText.set(el.value);
    // Reset height so shrinking is detected correctly
    el.style.height = 'auto';
    // Cap at 8rem (128px)
    el.style.height = Math.min(el.scrollHeight, 128) + 'px';
  }

  /**
   * Returns the last 10 completed turns for conversation history.
   * Excludes the pending user message and assistant placeholder
   * (those are added AFTER buildHistory() is called in send()).
   */
  private buildHistory(): ChatTurn[] {
    const msgs = this.messages();
    // Take last 10 completed messages (no streaming placeholder here yet)
    return msgs.slice(-10).map((m) => ({
      role: m.role as 'user' | 'assistant',
      content: m.content,
    })).filter((m) => m.role === 'user' || m.role === 'assistant');
  }

  /** Converts a subset of markdown to safe HTML for display. */
  renderMarkdown(text: string): SafeHtml {
    let html = text
      // Escape HTML entities first to prevent XSS
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      // Bold: **text** or __text__
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/__(.+?)__/g, '<strong>$1</strong>')
      // Italic: *text* or _text_
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/_(.+?)_/g, '<em>$1</em>')
      // Inline code: `code`
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      // Unordered list items: lines starting with "- " or "* "
      .replace(/^[\-\*] (.+)$/gm, '<li>$1</li>')
      // Wrap consecutive <li> in <ul>
      .replace(/(<li>[\s\S]+?<\/li>)(?!\s*<li>)/g, '<ul>$1</ul>')
      // Numbered list items
      .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
      // Double newline → paragraph break
      .replace(/\n\n+/g, '</p><p>')
      // Single newline → <br>
      .replace(/\n/g, '<br>');

    return this.sanitizer.bypassSecurityTrustHtml(`<p>${html}</p>`);
  }

  private abortActiveStream(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
  }
}
