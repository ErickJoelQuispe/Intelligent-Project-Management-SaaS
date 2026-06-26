/**
 * Represents an active Keycloak session for the authenticated user.
 *
 * Returned by GET /api/v1/auth/sessions.
 * Timestamps are ISO-8601 strings (UTC) as formatted by the backend.
 */
export interface UserSession {
  sessionId: string;
  ipAddress: string;
  started: string;    // ISO-8601 string from backend
  lastAccess: string; // ISO-8601 string from backend
}
