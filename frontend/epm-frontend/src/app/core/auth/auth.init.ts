import { APP_INITIALIZER, EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';

function initializeOAuth(oauthService: OAuthService): () => Promise<void> {
  return async () => {
    oauthService.configure(authConfig);
    await oauthService.loadDiscoveryDocument();
    oauthService.setupAutomaticSilentRefresh();
    await oauthService.tryLoginCodeFlow();
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
