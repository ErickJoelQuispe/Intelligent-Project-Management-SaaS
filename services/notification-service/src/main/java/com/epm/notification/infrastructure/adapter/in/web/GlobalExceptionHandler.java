package com.epm.notification.infrastructure.adapter.in.web;

import java.net.URI;

import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and infrastructure exceptions to RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 Not Found — notification not found. */
    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotFound(NotificationNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/notification-not-found"));
        problem.setTitle("Notification Not Found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "NOTIFICATION_NOT_FOUND");
        return problem;
    }

    /** 403 Forbidden — access denied. */
    @ExceptionHandler(NotificationAccessDeniedException.class)
    public ProblemDetail handleAccessDenied(NotificationAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://api.epm.com/errors/access-denied"));
        problem.setTitle("Access Denied");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "ACCESS_DENIED");
        return problem;
    }

    /**
     * 409 Conflict — unique constraint violation.
     *
     * <p>Handles the race condition in preference upsert (H6 fix): a concurrent PUT for the
     * same (user, type, channel) triggers the {@code uq_notif_pref_user_type_channel} constraint
     * and would otherwise reach the generic 500 handler. Mapping it to 409 gives callers a
     * semantically correct, retryable response instead of a misleading server error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation (concurrent write or duplicate key): {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/conflict"));
        problem.setTitle("Conflict");
        problem.setDetail("The request conflicts with the current state of the resource");
        problem.setProperty("errorCode", "CONFLICT");
        return problem;
    }

    /**
     * 400 Bad Request — Bean Validation constraint violation (e.g. @Min/@Max on request params).
     *
     * <p>Triggered by {@code @Validated} + {@code @Min}/{@code @Max} on controller method
     * parameters (H5 fix — pagination clamp). Spring throws {@link ConstraintViolationException}
     * when a param violates a constraint; without this handler it would fall through to the
     * generic 500 handler.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setDetail(ex.getMessage());
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
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://api.epm.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
