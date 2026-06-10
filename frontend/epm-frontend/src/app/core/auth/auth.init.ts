import { APP_INITIALIZER, EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';

function initializeOAuth(oauthService: OAuthService): () => Promise<void> {
  return async () => {
    oauthService.configure(authConfig);
    await oauthService.loadDiscoveryDocument();

    // Solo intentar el code flow si hay un ?code= en la URL
    // Si ya tenemos tokens válidos, setupAutomaticSilentRefresh los renueva solo
    const hasCodeInUrl = window.location.search.includes('code=');

    if (hasCodeInUrl) {
      // Hay un authorization code pendiente de intercambiar — procesarlo
      try {
        await oauthService.tryLoginCodeFlow();
        // Limpiar el ?code= de la URL sin recargar
        const cleanUrl = window.location.pathname;
        window.history.replaceState({}, '', cleanUrl);
      } catch {
        // El code ya fue usado o es inválido — iniciar login fresco
        oauthService.initCodeFlow();
      }
    } else if (!oauthService.hasValidAccessToken()) {
      // No hay code en URL y no hay token válido → ir a Keycloak
      oauthService.initCodeFlow();
    }

    // Con token válido, activar renovación automática
    if (oauthService.hasValidAccessToken()) {
      oauthService.setupAutomaticSilentRefresh();
    }
  };
}

export function provideAuthInitializer(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: APP_INITIALIZER,
      useFactory: (oauthService: OAuthService) => initializeOAuth(oauthService),
      deps: [OAuthService],
      multi: true,
    },
  ]);
}
