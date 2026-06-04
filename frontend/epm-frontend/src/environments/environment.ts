export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  wsBaseUrl: 'ws://localhost:8080/ws/notifications',
  keycloak: {
    issuer: 'http://localhost:8180/realms/epm',
    clientId: 'epm-frontend',
    get redirectUri() { return window.location.origin + '/'; },
    scope: 'openid profile email',
  },
};
