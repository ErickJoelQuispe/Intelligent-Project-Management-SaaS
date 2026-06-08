package com.epm.task.domain.port.out;

import com.epm.task.domain.model.ActivityLog;

/**
 * Driven port for activity log persistence.
 */
public interface ActivityLogRepository {

    ActivityLog save(ActivityLog activityLog);
}
