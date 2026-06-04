package com.epm.notification.infrastructure.adapter.in.web;

import java.net.URI;

import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
