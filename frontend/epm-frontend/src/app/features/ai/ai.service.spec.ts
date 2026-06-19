import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OAuthService } from 'angular-oauth2-oidc';
import { AiService } from './ai.service';
import { ChatResponse } from './models/chat.models';

const BASE_URL = 'http://localhost:8080/api/v1/ai';

const oauthServiceMock = {
  getAccessToken: () => 'mock-token',
};

describe('AiService', () => {
  let service: AiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OAuthService, useValue: oauthServiceMock },
      ],
    });
    service = TestBed.inject(AiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('generates tasks via POST', () => {
    const expected = {
      tasks: [{ title: 'Task 1', description: 'Desc', priority: 'HIGH' as const }],
      cached: false,
    };

    service.generateTasks('proj-1', 'Build a login').subscribe((res) => {
      expect(res.tasks.length).toBe(1);
      expect(res.cached).toBe(false);
    });

    const req = httpMock.expectOne(`${BASE_URL}/tasks/generate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ projectId: 'proj-1', description: 'Build a login' });
    req.flush(expected);
  });

  it('gets project summary via POST', () => {
    const expected = { summary: 'Project is on track', cached: true };

    service.getProjectSummary('proj-1').subscribe((res) => {
      expect(res.summary).toBe('Project is on track');
      expect(res.cached).toBe(true);
    });

    const req = httpMock.expectOne(`${BASE_URL}/projects/proj-1/summary`);
    expect(req.request.method).toBe('POST');
    req.flush(expected);
  });

  // T5.1 — chat() POST method
  describe('chat()', () => {
    it('sends POST to /ai/chat with message and projectId, maps reply', () => {
      const mockResponse: ChatResponse = { reply: 'Hello from AI' };
      let result: ChatResponse | undefined;

      service.chat('Hello', 'proj-1').subscribe((res) => {
        result = res;
      });

      const req = httpMock.expectOne(`${BASE_URL}/chat`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ message: 'Hello', projectId: 'proj-1' });
      req.flush(mockResponse);

      expect(result?.reply).toBe('Hello from AI');
    });

    it('always includes projectId in body (required by backend @NotBlank)', () => {
      const mockResponse: ChatResponse = { reply: 'AI reply' };
      let result: ChatResponse | undefined;

      service.chat('Hello', 'proj-42').subscribe((res) => {
        result = res;
      });

      const req = httpMock.expectOne(`${BASE_URL}/chat`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ message: 'Hello', projectId: 'proj-42' });
      req.flush(mockResponse);

      expect(result?.reply).toBe('AI reply');
    });
  });

  // T5.2 — streamChat() Observable with fetch + ReadableStream
  describe('streamChat()', () => {
    let originalFetch: typeof globalThis.fetch;

    beforeEach(() => {
      originalFetch = globalThis.fetch;
    });

    afterEach(() => {
      globalThis.fetch = originalFetch;
    });

    it('uses POST with JSON body (not GET with query params)', async () => {
      let capturedMethod: string | undefined;
      let capturedBody: string | undefined;
      let capturedUrl: string | undefined;

      const stream = new ReadableStream({ start(c) { c.close(); } });
      globalThis.fetch = (url: RequestInfo | URL, init?: RequestInit) => {
        capturedUrl    = url.toString();
        capturedMethod = init?.method;
        capturedBody   = init?.body as string;
        return Promise.resolve({ ok: true, body: stream } as Response);
      };

      await new Promise<void>((resolve) => {
        service.streamChat('test', 'proj-1').subscribe({ complete: resolve, error: resolve });
      });

      expect(capturedMethod).toBe('POST');
      expect(capturedUrl).not.toContain('?');
      const body = JSON.parse(capturedBody ?? '{}');
      expect(body['message']).toBe('test');
      expect(body['projectId']).toBe('proj-1');
    });

    it('skips event: and id: SSE metadata lines', async () => {
      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('event: message\ndata: Hello\n\n'));
          controller.enqueue(encoder.encode('id: 1\ndata: World\n\n'));
          controller.enqueue(encoder.encode('retry: 3000\n\n'));
          controller.close();
        },
      });

      globalThis.fetch = () => Promise.resolve({ ok: true, body: stream } as Response);

      const tokens: string[] = [];
      await new Promise<void>((resolve, reject) => {
        service.streamChat('test', 'proj-1').subscribe({
          next: (t) => tokens.push(t),
          complete: resolve,
          error: reject,
        });
      });

      expect(tokens).toEqual(['Hello', 'World']);
    });

    it('emits tokens from SSE data: lines', async () => {
      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('data: Hello\n\n'));
          controller.enqueue(encoder.encode('data:  World\n\n'));
          controller.close();
        },
      });

      globalThis.fetch = () =>
        Promise.resolve({ ok: true, body: stream } as Response);

      const tokens: string[] = [];
      await new Promise<void>((resolve, reject) => {
        service.streamChat('test message', 'proj-1').subscribe({
          next: (token) => tokens.push(token),
          complete: () => resolve(),
          error: reject,
        });
      });

      expect(tokens).toEqual(['Hello', ' World']);
    });

    it('emits tokens from raw text chunks (no data: prefix)', async () => {
      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('token1'));
          controller.enqueue(encoder.encode('token2'));
          controller.close();
        },
      });

      globalThis.fetch = () =>
        Promise.resolve({ ok: true, body: stream } as Response);

      const tokens: string[] = [];
      await new Promise<void>((resolve, reject) => {
        service.streamChat('raw chunks test').subscribe({
          next: (token) => tokens.push(token),
          complete: () => resolve(),
          error: reject,
        });
      });

      expect(tokens.length).toBeGreaterThan(0);
      expect(tokens.join('')).toContain('token1');
    });

    it('skips [DONE] sentinel and empty lines', async () => {
      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('data: First\n\n'));
          controller.enqueue(encoder.encode('\n'));
          controller.enqueue(encoder.encode('data: [DONE]\n\n'));
          controller.close();
        },
      });

      globalThis.fetch = () =>
        Promise.resolve({ ok: true, body: stream } as Response);

      const tokens: string[] = [];
      await new Promise<void>((resolve, reject) => {
        service.streamChat('done test').subscribe({
          next: (token) => tokens.push(token),
          complete: () => resolve(),
          error: reject,
        });
      });

      expect(tokens).toEqual(['First']);
    });

    it('aborts the fetch signal on unsubscribe', async () => {
      let capturedSignal: AbortSignal | undefined;
      const encoder = new TextEncoder();

      // A stream that sends one token then never closes
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('data: token\n\n'));
          // Never calls close() — subscription must abort it
        },
      });

      globalThis.fetch = (_url: RequestInfo | URL, init?: RequestInit) => {
        capturedSignal = init?.signal ?? undefined;
        return Promise.resolve({ ok: true, body: stream } as Response);
      };

      const tokens: string[] = [];
      const sub = service.streamChat('abort test').subscribe({
        next: (t) => tokens.push(t),
        error: () => {},
      });

      // Wait for fetch to start
      await new Promise<void>((r) => setTimeout(r, 20));
      sub.unsubscribe();
      await new Promise<void>((r) => setTimeout(r, 20));

      expect(capturedSignal).toBeDefined();
      expect(capturedSignal?.aborted).toBe(true);
    });
  });
});
