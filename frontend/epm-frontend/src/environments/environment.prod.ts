export const environment = {
  production: true,
  apiBaseUrl: 'https://api.epm.example.com/api/v1',
  wsBaseUrl: 'wss://api.epm.example.com/ws/notifications',
  keycloak: {
    issuer: 'https://auth.epm.example.com/realms/epm',
    clientId: 'epm-frontend',
    get redirectUri() { return window.location.origin + '/'; },
    scope: 'openid profile email',
  },
};
