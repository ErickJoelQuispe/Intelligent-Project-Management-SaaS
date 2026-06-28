import { TestBed } from '@angular/core/testing';
import { AppPreferencesService } from './app-preferences.service';
import { provideTranslocoTesting } from '../../testing/transloco-testing';

describe('AppPreferencesService', () => {
  let service: AppPreferencesService;
  let getItemSpy: ReturnType<typeof vi.spyOn>;
  let setItemSpy: ReturnType<typeof vi.spyOn>;

  function setup(stored: Record<string, string> = {}) {
    getItemSpy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(
      (key: string) => stored[key] ?? null,
    );
    setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [AppPreferencesService, ...provideTranslocoTesting()],
    });

    service = TestBed.inject(AppPreferencesService);
  }

  afterEach(() => {
    vi.restoreAllMocks();
    // Clean up any CSS vars set on :root
    document.documentElement.style.removeProperty('--app-compact-mode');
    document.documentElement.style.removeProperty('--app-reduce-animations');
  });

  // ─── Initialization: compactMode ──────────────────────────────────────────

  describe('init — compactMode', () => {
    it('should restore compactMode to true when localStorage stores "true"', () => {
      setup({ 'app.compactMode': 'true' });

      expect(service.compactMode()).toBe(true);
    });

    it('should default compactMode to false when localStorage key is absent', () => {
      setup();

      expect(service.compactMode()).toBe(false);
    });
  });

  // ─── Initialization: reduceAnimations ────────────────────────────────────

  describe('init — reduceAnimations', () => {
    it('should restore reduceAnimations to true when localStorage stores "true"', () => {
      setup({ 'app.reduceAnimations': 'true' });

      expect(service.reduceAnimations()).toBe(true);
    });

    it('should default reduceAnimations to false when localStorage key is absent', () => {
      setup();

      expect(service.reduceAnimations()).toBe(false);
    });
  });

  // ─── setCompactMode ───────────────────────────────────────────────────────

  describe('setCompactMode()', () => {
    it('should set localStorage["app.compactMode"] to "true" and apply --app-compact-mode: 1', () => {
      setup();

      service.setCompactMode(true);

      expect(setItemSpy).toHaveBeenCalledWith('app.compactMode', 'true');
      expect(document.documentElement.style.getPropertyValue('--app-compact-mode')).toBe('1');
    });

    it('should set localStorage["app.compactMode"] to "false" and apply --app-compact-mode: 0', () => {
      setup();

      service.setCompactMode(false);

      expect(setItemSpy).toHaveBeenCalledWith('app.compactMode', 'false');
      expect(document.documentElement.style.getPropertyValue('--app-compact-mode')).toBe('0');
    });
  });

  // ─── setReduceAnimations ──────────────────────────────────────────────────

  describe('setReduceAnimations()', () => {
    it('should set localStorage["app.reduceAnimations"] to "true" and apply --app-reduce-animations: 1', () => {
      setup();

      service.setReduceAnimations(true);

      expect(setItemSpy).toHaveBeenCalledWith('app.reduceAnimations', 'true');
      expect(document.documentElement.style.getPropertyValue('--app-reduce-animations')).toBe('1');
    });

    it('should set localStorage["app.reduceAnimations"] to "false" and apply --app-reduce-animations: 0', () => {
      setup();

      service.setReduceAnimations(false);

      expect(setItemSpy).toHaveBeenCalledWith('app.reduceAnimations', 'false');
      expect(document.documentElement.style.getPropertyValue('--app-reduce-animations')).toBe('0');
    });
  });
});
