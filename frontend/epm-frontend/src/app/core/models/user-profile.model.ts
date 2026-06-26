export interface UserPreferences {
  language: string;
  timezone: string;
  dateFormat: string;
  startOfWeek: string;
}

export const DEFAULT_PREFERENCES: UserPreferences = {
  language: 'en',
  timezone: 'UTC',
  dateFormat: 'ISO',
  startOfWeek: 'MONDAY',
};

export interface UserProfile {
  id: string;
  tenantId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  bio: string | null;
  avatarUrl: string | null;
  version: number;
  preferences: UserPreferences | null;
}

export interface TenantUser {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  bio?: string;
  avatarUrl?: string;
  version: number; // required — optimistic lock
  preferences?: UserPreferences;
}
