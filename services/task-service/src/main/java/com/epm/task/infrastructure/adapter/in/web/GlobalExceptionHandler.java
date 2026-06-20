package com.epm.task.infrastructure.adapter.in.web;

import java.net.URI;

import com.epm.task.domain.exception.InvalidStatusException;
import com.epm.task.domain.exception.MaxDepthExceededException;
import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.exception.TenantRequiredException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps domain exceptions to RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 Not Found — task not found. */
    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFound(TaskNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/task-not-found"));
        problem.setTitle("Task Not Found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "TASK_NOT_FOUND");
        return problem;
    }

    /** 422 Unprocessable Entity — max depth exceeded. */
    @ExceptionHandler(MaxDepthExceededException.class)
    public ProblemDetail handleMaxDepth(MaxDepthExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setType(URI.create("https://api.epm.com/errors/max-depth-exceeded"));
        problem.setTitle("Max Depth Exceeded");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "MAX_DEPTH_EXCEEDED");
        return problem;
    }

    /** 403 Forbidden — project membership required. */
    @ExceptionHandler(ProjectMembershipRequiredException.class)
    public ProblemDetail handleMembership(ProjectMembershipRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://api.epm.com/errors/project-membership-required"));
        problem.setTitle("Project Membership Required");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "PROJECT_MEMBERSHIP_REQUIRED");
        return problem;
    }

    /**
     * 503 Service Unavailable — project-service circuit breaker open.
     *
     * <p>Sets {@code Retry-After} as an HTTP response header per RFC 7231 §7.1.3.
     * The header value is NOT included in the JSON body.
     */
    @ExceptionHandler(ProjectServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailable(ProjectServiceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("https://api.epm.com/errors/project-service-unavailable"));
        problem.setTitle("Project Service Unavailable");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "PROJECT_SERVICE_UNAVAILABLE");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "30");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).headers(headers).body(problem);
    }

    /** 400 Bad Request — tenant required. */
    @ExceptionHandler(TenantRequiredException.class)
    public ProblemDetail handleTenantRequired(TenantRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/tenant-required"));
        problem.setTitle("Tenant Required");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "TENANT_REQUIRED");
        return problem;
    }

    /**
     * 409 Conflict — invalid status string or FSM transition violation.
     *
     * <p>Covers two cases:
     * <ul>
     *   <li>Controller: an unrecognised status string in the request path.</li>
     *   <li>Domain FSM: a {@code changeStatus()} call that violates the allowed transition
     *       table (e.g., DONE → IN_REVIEW, which is not permitted).</li>
     * </ul>
     * 409 is the appropriate code for a structurally valid request that conflicts with the
     * current state of the resource (RFC 7231 §6.5.8).
     */
    @ExceptionHandler(InvalidStatusException.class)
    public ProblemDetail handleInvalidStatus(InvalidStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/invalid-status"));
        problem.setTitle("Invalid Status Transition");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "INVALID_STATUS");
        return problem;
    }

    /** 409 Conflict — optimistic locking failure (concurrent update). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/conflict"));
        problem.setTitle("Conflict");
        problem.setDetail("The resource was modified concurrently. Please retry.");
        problem.setProperty("errorCode", "OPTIMISTIC_LOCK_CONFLICT");
        return problem;
    }

    /** 400 Bad Request — bean validation failure (request body). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        problem.setDetail(detail);
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        return problem;
    }

    /** 400 Bad Request — constraint violation (query params via {@code @Validated}). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Validation failed");
        problem.setDetail(detail);
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        return problem;
    }

    /** 400 Bad Request — illegal argument. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "BAD_REQUEST");
        return problem;
    }

    /** 404 Not Found — static resource not found (e.g. actuator scrape on missing endpoint). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/not-found"));
        problem.setTitle("Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 500 Internal Server Error — unexpected exception. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://api.epm.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
