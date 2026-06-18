package com.epm.user.infrastructure.adapter.in.rest;

import java.net.URI;

import com.epm.user.domain.exception.DuplicateMemberException;
import com.epm.user.domain.exception.InvalidTeamNameException;
import com.epm.user.domain.exception.LastOwnerException;
import com.epm.user.domain.exception.OptimisticLockException;
import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.exception.UserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 Not Found — profile not found. */
    @ExceptionHandler(ProfileNotFoundException.class)
    public ProblemDetail handleProfileNotFound(ProfileNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/profile-not-found"));
        problem.setTitle("Profile Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 404 Not Found — team not found (or caller not a member). */
    @ExceptionHandler(TeamNotFoundException.class)
    public ProblemDetail handleTeamNotFound(TeamNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/team-not-found"));
        problem.setTitle("Team Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 404 Not Found — user not found. */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/user-not-found"));
        problem.setTitle("User Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — duplicate team member. */
    @ExceptionHandler(DuplicateMemberException.class)
    public ProblemDetail handleDuplicateMember(DuplicateMemberException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/duplicate-member"));
        problem.setTitle("Duplicate Member");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — last owner removal attempt. */
    @ExceptionHandler(LastOwnerException.class)
    public ProblemDetail handleLastOwner(LastOwnerException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/last-owner"));
        problem.setTitle("Last Owner");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — domain optimistic locking failure (version mismatch detected in domain). */
    @ExceptionHandler(OptimisticLockException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/optimistic-lock"));
        problem.setTitle("Optimistic Lock Conflict");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * 409 Conflict — JPA {@code @Version} optimistic lock failure.
     *
     * <p>Spring wraps {@code jakarta.persistence.OptimisticLockException} in
     * {@link ObjectOptimisticLockingFailureException} before it reaches advice.
     * Without this handler the exception would fall through to the generic 500 handler.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleJpaOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/optimistic-lock"));
        problem.setTitle("Optimistic Lock Conflict");
        problem.setDetail("Concurrent modification detected — please retry with the latest version");
        return problem;
    }

    /** 403 Forbidden — unauthorized operation. */
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://api.epm.com/errors/forbidden"));
        problem.setTitle("Forbidden");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 400 Bad Request — invalid team name (blank or exceeds max length). */
    @ExceptionHandler(InvalidTeamNameException.class)
    public ProblemDetail handleInvalidTeamName(InvalidTeamNameException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/invalid-team-name"));
        problem.setTitle("Invalid Team Name");
        problem.setDetail(ex.getMessage());
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
        return problem;
    }

    /**
     * 400 Bad Request — invalid request-parameter constraint (e.g. {@code @Min} on
     * {@code @RequestParam} under {@code @Validated}).
     *
     * <p>Spring Framework 6.1+ (Spring Boot 3.2+) raises
     * {@link HandlerMethodValidationException} for method-level validation of
     * controller parameters. Without this handler it would fall through to the
     * generic 500 handler.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more request parameters are invalid");
        return problem;
    }

    /**
     * 400 Bad Request — bean-validation constraint violation.
     *
     * <p>Backstop for the legacy method-validation path that raises
     * {@link ConstraintViolationException} (e.g. when method validation is handled
     * by the {@code MethodValidationPostProcessor} rather than Spring MVC).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        String detail = ex.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");
        problem.setDetail(detail);
        return problem;
    }

    /** 400 Bad Request — illegal argument (backstop, e.g. out-of-range pagination). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://api.epm.com/errors/invalid-request"));
        problem.setTitle("Invalid Request");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 500 Internal Server Error — unexpected exception. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://api.epm.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
