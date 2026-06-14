package com.epm.project.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.ListProjectsUseCase;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link ListProjectsUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Results are DB-paginated; the {@code size} parameter is capped at
 * {@value #MAX_PAGE_SIZE} and {@code page} is floored at 0 to prevent runaway
 * queries (DoS) and malformed page requests, regardless of how the adapter validated
 * the input.
 */
public class ListProjectsUseCaseImpl implements ListProjectsUseCase {

    static final int MAX_PAGE_SIZE = 100;

    private final ProjectRepository projectRepository;

    public ListProjectsUseCaseImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public List<ProjectResult> execute(UUID callerProfileId, UUID tenantId) {
        return execute(callerProfileId, tenantId, false, 0, MAX_PAGE_SIZE);
    }

    @Override
    public List<ProjectResult> execute(UUID callerProfileId, UUID tenantId, boolean includeArchived,
            int page, int size) {
        // Defensive clamping so the page request can never be unbounded or malformed:
        // page >= 0, 1 <= size <= MAX_PAGE_SIZE.
        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        if (includeArchived) {
            return projectRepository
                    .findPageByMemberProfileId(callerProfileId, tenantId, effectivePage, effectiveSize)
                    .stream()
                    .map(CreateProjectUseCaseImpl::toResult)
                    .toList();
        }
        return projectRepository
                .findPageByMemberProfileIdExcludingArchived(
                        callerProfileId, tenantId, effectivePage, effectiveSize)
                .stream()
                .map(CreateProjectUseCaseImpl::toResult)
                .toList();
    }
}
