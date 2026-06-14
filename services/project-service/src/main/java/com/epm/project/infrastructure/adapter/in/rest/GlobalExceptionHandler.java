package com.epm.project.infrastructure.adapter.in.rest;

import java.net.URI;

import com.epm.project.domain.exception.DuplicateProjectMemberException;
import com.epm.project.domain.exception.DuplicateTeamAssignmentException;
import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps domain exceptions to RFC 7807 Problem Details responses.
 *
 * <p>Uses Spring 6 built-in {@link ProblemDetail} for standard error bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 Not Found — project not found. */
    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleProjectNotFound(ProjectNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/project-not-found"));
        problem.setTitle("Project Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 403 Forbidden — unauthorized project access. */
    @ExceptionHandler(UnauthorizedProjectAccessException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedProjectAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://api.epm.com/errors/forbidden"));
        problem.setTitle("Forbidden");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — duplicate team assignment. */
    @ExceptionHandler(DuplicateTeamAssignmentException.class)
    public ProblemDetail handleDuplicateTeam(DuplicateTeamAssignmentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/duplicate-team-assignment"));
        problem.setTitle("Duplicate Team Assignment");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — duplicate project member. */
    @ExceptionHandler(DuplicateProjectMemberException.class)
    public ProblemDetail handleDuplicateMember(DuplicateProjectMemberException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/duplicate-project-member"));
        problem.setTitle("Duplicate Project Member");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — optimistic locking failure (concurrent modification). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/concurrent-modification"));
        problem.setTitle("Concurrent Modification");
        problem.setDetail("The resource was modified by another request. Please retry.");
        return problem;
    }

    /** 400 Bad Request — domain invariant violation (blank name, oversized input, etc.). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/invalid-argument"));
        problem.setTitle("Invalid Argument");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 400 Bad Request — bean validation failure on @RequestBody. */
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
        return problem;
    }

    /** 400 Bad Request — @Validated @RequestParam / @PathVariable constraint violations. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 400 Bad Request — JSR-303 constraint violations (e.g. @Validated on service beans). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Constraint violation");
        problem.setDetail(detail);
        return problem;
    }

    /** 500 Internal Server Error — unexpected exception. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://api.epm.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
