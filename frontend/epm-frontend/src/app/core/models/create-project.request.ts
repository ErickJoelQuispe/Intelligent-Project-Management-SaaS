import { ProjectVisibility } from './project.model';

export interface CreateProjectRequest {
  name: string;
  description?: string;
  visibility: ProjectVisibility;
}
