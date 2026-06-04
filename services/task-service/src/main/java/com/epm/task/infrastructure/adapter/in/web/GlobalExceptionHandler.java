package com.epm.task.infrastructure.adapter.in.web;

import java.net.URI;

import com.epm.task.domain.exception.InvalidStatusException;
import com.epm.task.domain.exception.MaxDepthExceededException;
import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.exception.TenantRequiredException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    /** 400 Bad Request — invalid status. */
    @ExceptionHandler(InvalidStatusException.class)
    public ProblemDetail handleInvalidStatus(InvalidStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/invalid-status"));
        problem.setTitle("Invalid Status");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "INVALID_STATUS");
        return problem;
    }

    /** 400 Bad Request — bean validation failure. */
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
