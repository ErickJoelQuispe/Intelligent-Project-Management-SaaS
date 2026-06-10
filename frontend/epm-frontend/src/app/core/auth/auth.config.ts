import { AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';

export const authConfig: AuthConfig = {
  issuer: environment.keycloak.issuer,
  clientId: environment.keycloak.clientId,
  redirectUri: environment.keycloak.redirectUri,
  responseType: 'code',
  scope: environment.keycloak.scope,
  showDebugInformation: !environment.production,
  clearHashAfterLogin: false,
  // PKCE + refresh_token grant — sin iframe
  useSilentRefresh: false,
  timeoutFactor: 0.75,
};
