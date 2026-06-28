import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, throwError, Subject } from 'rxjs';
import { AiChatComponent } from './ai-chat.component';
import { AiService } from '../ai.service';
import { provideTranslocoTesting } from '../../../testing/transloco-testing';

function makeAiServiceStub(streamSource: Subject<string>) {
  return {
    streamChat: () => streamSource.asObservable(),
  } as unknown as AiService;
}

describe('AiChatComponent', () => {
  let component: AiChatComponent;
  let fixture: ComponentFixture<AiChatComponent>;
  let streamSubject: Subject<string>;
  let aiServiceStub: AiService;

  beforeEach(async () => {
    streamSubject = new Subject<string>();
    aiServiceStub = makeAiServiceStub(streamSubject);

    await TestBed.configureTestingModule({
      imports: [AiChatComponent],
      providers: [{ provide: AiService, useValue: aiServiceStub }, ...provideTranslocoTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AiChatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  // T5.3 — send() pushes user message immediately
  describe('send()', () => {
    it('appends a user message immediately when send is called', () => {
      component.inputText.set('Hello AI');

      component.send();

      const messages = component.messages();
      expect(messages.length).toBeGreaterThan(0);
      expect(messages[0].role).toBe('user');
      expect(messages[0].content).toBe('Hello AI');
    });

    it('does not send when inputText is empty', () => {
      component.inputText.set('');
      component.send();
      expect(component.messages().length).toBe(0);
    });

    it('does not send when inputText is only whitespace', () => {
      component.inputText.set('   ');
      component.send();
      expect(component.messages().length).toBe(0);
    });

    it('clears the input after sending', () => {
      component.inputText.set('Hello');
      component.send();
      expect(component.inputText()).toBe('');
    });

    it('sets loading to true while stream is active', () => {
      component.inputText.set('Hello');
      component.send();
      expect(component.loading()).toBe(true);
    });

    it('sets loading to false after stream completes', () => {
      component.inputText.set('Hello');
      component.send();

      streamSubject.complete();
      fixture.detectChanges();

      expect(component.loading()).toBe(false);
    });

    it('appends assistant placeholder with streaming=true before tokens arrive', () => {
      component.inputText.set('Hello');
      component.send();

      const messages = component.messages();
      const assistantMsg = messages.find((m) => m.role === 'assistant');
      expect(assistantMsg).toBeDefined();
      expect(assistantMsg?.streaming).toBe(true);
    });

    it('appends tokens to the assistant message content', () => {
      component.inputText.set('Hello');
      component.send();

      streamSubject.next('token1');
      streamSubject.next(' token2');
      fixture.detectChanges();

      const assistantMsg = component.messages().find((m) => m.role === 'assistant');
      expect(assistantMsg?.content).toBe('token1 token2');
    });

    it('marks assistant message streaming=false on complete', () => {
      component.inputText.set('Hello');
      component.send();

      streamSubject.next('reply');
      streamSubject.complete();
      fixture.detectChanges();

      const assistantMsg = component.messages().find((m) => m.role === 'assistant');
      expect(assistantMsg?.streaming).toBe(false);
    });

    it('appends error message with role error on stream error', () => {
      component.inputText.set('Hello');
      component.send();

      streamSubject.error(new Error('Network failure'));
      fixture.detectChanges();

      const errorMsg = component.messages().find((m) => m.role === 'error');
      expect(errorMsg).toBeDefined();
      expect(component.loading()).toBe(false);
    });
  });

  // T5.4 — Integration smoke: component renders in DOM
  describe('template', () => {
    it('shows empty state when no messages exist', () => {
      expect(component.messages().length).toBe(0);
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="empty-state"]')).toBeTruthy();
    });

    it('renders user bubble after send()', () => {
      component.inputText.set('Hello AI');
      component.send();
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="message-user"]')).toBeTruthy();
    });

    it('renders assistant bubble after tokens arrive', () => {
      component.inputText.set('Hello');
      component.send();
      streamSubject.next('reply');
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="message-assistant"]')).toBeTruthy();
    });

    it('disables send button while loading', () => {
      component.inputText.set('Hello');
      component.send();
      fixture.detectChanges();

      // The data-testid is placed directly on the <button> element in the template.
      const btn = fixture.debugElement.query(By.css('[data-testid="send-button"]'));
      expect(btn?.nativeElement?.disabled).toBe(true);
    });
  });
});
