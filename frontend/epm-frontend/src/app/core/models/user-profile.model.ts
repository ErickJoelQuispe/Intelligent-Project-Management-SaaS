export interface UserProfile {
  id: string;
  tenantId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  bio: string | null;
  avatarUrl: string | null;
  version: number;
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
}
