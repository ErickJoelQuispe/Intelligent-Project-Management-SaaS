package com.epm.project.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: lists projects accessible to the caller.
 */
public interface ListProjectsUseCase {

    List<ProjectResult> execute(UUID callerProfileId, UUID tenantId);

    /**
     * Lists a page of projects accessible to the caller, optionally including archived ones.
     *
     * <p>Pagination is pushed to SQL (LIMIT/OFFSET). The {@code page} and {@code size}
     * arguments are clamped defensively by the implementation so an out-of-range value
     * can never produce an unbounded query or a malformed page request.
     *
     * @param callerProfileId the profile making the request
     * @param tenantId        the caller's tenant
     * @param includeArchived when {@code true}, archived projects are included in the result
     * @param page            zero-based page index (clamped to {@code >= 0})
     * @param size            page size (clamped to {@code 1..MAX_PAGE_SIZE})
     * @return a bounded list of matching projects
     */
    List<ProjectResult> execute(UUID callerProfileId, UUID tenantId, boolean includeArchived,
            int page, int size);
}
