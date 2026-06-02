export const environment = {
  production: true,
  apiBaseUrl: 'https://api.epm.example.com/api/v1',
  keycloak: {
    issuer: 'https://auth.epm.example.com/realms/epm',
    clientId: 'epm-frontend',
    redirectUri: window.location.origin + '/',
    scope: 'openid profile email',
  },
};
