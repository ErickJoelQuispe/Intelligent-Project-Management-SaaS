import { EnvironmentProviders, inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  provideTransloco,
  TranslocoLoader,
} from '@jsverse/transloco';
import { provideLanguageInitializer } from './language.init';

@Injectable({ providedIn: 'root' })
export class AppTranslocoLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(lang: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`assets/i18n/${lang}.json`);
  }
}

/**
 * Configures Transloco with HTTP loader, available languages, and language
 * bootstrap from localStorage via provideLanguageInitializer().
 *
 * Add to providers in app.config.ts.
 */
export function provideAppTransloco(): EnvironmentProviders[] {
  return [
    ...provideTransloco({
      config: {
        availableLangs: ['en', 'es', 'pt'],
        defaultLang: 'en',
        reRenderOnLangChange: true,
        prodMode: false,
      },
      loader: AppTranslocoLoader,
    }),
    provideLanguageInitializer(),
  ];
}
