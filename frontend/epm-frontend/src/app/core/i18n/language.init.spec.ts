import { SUPPORTED_LANGS, resolveLanguage } from './language.init';

/**
 * Tests for the pure resolveLanguage() function and SUPPORTED_LANGS constant.
 * The APP_INITIALIZER factory itself is tested indirectly via the pure function.
 */
describe('resolveLanguage()', () => {
  it('returns "en" when value is null (key missing from localStorage)', () => {
    expect(resolveLanguage(null)).toBe('en');
  });

  it('returns "en" for an unsupported lang code "fr"', () => {
    expect(resolveLanguage('fr')).toBe('en');
  });

  it('returns "en" for empty string', () => {
    expect(resolveLanguage('')).toBe('en');
  });

  it('returns "es" when value is "es"', () => {
    expect(resolveLanguage('es')).toBe('es');
  });

  it('returns "pt" when value is "pt"', () => {
    expect(resolveLanguage('pt')).toBe('pt');
  });

  it('returns "en" when value is "en"', () => {
    expect(resolveLanguage('en')).toBe('en');
  });
});

describe('SUPPORTED_LANGS', () => {
  it('contains exactly en, es, pt', () => {
    expect(SUPPORTED_LANGS).toEqual(['en', 'es', 'pt']);
  });
});
