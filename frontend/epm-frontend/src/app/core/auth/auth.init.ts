import { APP_INITIALIZER, EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';

/** Public route prefixes that must NOT trigger a Keycloak redirect. */
const PUBLIC_PATH_PREFIXES = ['/register', '/accept-invitation'] as const;

/**
 * Returns true when the app should skip `initCodeFlow()` for the given pathname.
 * Exported for unit-testing.
 */
export function shouldSkipCodeFlow(pathname: string): boolean {
  return PUBLIC_PATH_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(prefix + '/'),
  );
}

function initializeOAuth(oauthService: OAuthService): () => Promise<void> {
  return async () => {
    // Skip Keycloak redirect on public registration / invitation routes
    if (shouldSkipCodeFlow(window.location.pathname)) {
      return;
    }

    oauthService.configure(authConfig);
    await oauthService.loadDiscoveryDocument();

    // Only attempt the code flow if there is a ?code= in the URL.
    // When tokens are already valid, setupAutomaticSilentRefresh renews them automatically.
    const hasCodeInUrl = window.location.search.includes('code=');

    if (hasCodeInUrl) {
      // An authorization code is pending exchange — process it
      try {
        await oauthService.tryLoginCodeFlow();
        // Remove ?code= from the URL without reloading
        const cleanUrl = window.location.pathname;
        window.history.replaceState({}, '', cleanUrl);
      } catch {
        // Code was already used or is invalid — start a fresh login
        oauthService.initCodeFlow();
      }
    } else if (!oauthService.hasValidAccessToken()) {
      // No code in URL and no valid token → redirect to Keycloak
      oauthService.initCodeFlow();
    }

    // With a valid token, activate automatic silent refresh
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
