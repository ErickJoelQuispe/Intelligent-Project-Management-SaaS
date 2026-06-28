import { APP_INITIALIZER, EnvironmentProviders, inject, makeEnvironmentProviders } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

export const SUPPORTED_LANGS: string[] = ['en', 'es', 'pt'];

/**
 * Resolves a raw localStorage value to a supported language code.
 * Returns 'en' if the value is null, empty, or not in SUPPORTED_LANGS.
 */
export function resolveLanguage(raw: string | null): string {
  if (!raw || !SUPPORTED_LANGS.includes(raw)) {
    return 'en';
  }
  return raw;
}

function initializeLanguage(translocoService: TranslocoService): () => void {
  return () => {
    const raw = localStorage.getItem('app.language');
    const lang = resolveLanguage(raw);
    translocoService.setActiveLang(lang);
  };
}

/**
 * Provides an APP_INITIALIZER that reads the user's preferred language from
 * localStorage and applies it synchronously before any component renders.
 * Defaults to 'en' if no valid language is stored.
 */
export function provideLanguageInitializer(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: APP_INITIALIZER,
      useFactory: (translocoService: TranslocoService) =>
        initializeLanguage(translocoService),
      deps: [TranslocoService],
      multi: true,
    },
  ]);
}
