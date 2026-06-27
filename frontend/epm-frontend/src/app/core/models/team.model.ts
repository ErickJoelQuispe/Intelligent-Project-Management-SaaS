export type TeamRole = 'OWNER' | 'MEMBER' | 'VIEWER';

export interface TeamMember {
  userId: string;
  role: TeamRole;
  joinedAt: string;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
}

export interface Team {
  id: string;
  tenantId: string;
  ownerId: string;
  name: string;
  description?: string;
  members: TeamMember[];
}

export interface CreateTeamRequest {
  name: string;
  description?: string;
}

export interface AddMemberRequest {
  userId: string;
  role: TeamRole;
}

export interface UpdateTeamRequest {
  name?: string;
  description?: string;
}

export interface UpdateMemberRoleRequest {
  role: TeamRole;
}
