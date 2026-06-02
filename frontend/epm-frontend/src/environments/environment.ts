export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  keycloak: {
    issuer: 'http://localhost:8180/realms/epm',
    clientId: 'epm-frontend',
    redirectUri: window.location.origin + '/',
    scope: 'openid profile email',
  },
};
