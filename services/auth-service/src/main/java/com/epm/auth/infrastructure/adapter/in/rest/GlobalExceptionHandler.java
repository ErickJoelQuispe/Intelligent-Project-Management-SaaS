package com.epm.auth.infrastructure.adapter.in.rest;

import java.net.URI;

import com.epm.auth.domain.exception.DuplicateEmailException;
import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException;
import com.epm.auth.domain.exception.InvitationTokenExpiredException;
import com.epm.auth.domain.exception.InvitationTokenInvalidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 7807 Problem Details responses.
 *
 * <p>Uses Spring 6 built-in {@link ProblemDetail} for standard error bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 409 Conflict — duplicate email address. */
    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/duplicate-email"));
        problem.setTitle("Duplicate Email");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 503 Service Unavailable — identity provider unavailable (circuit breaker open). */
    @ExceptionHandler(IdentityProviderException.class)
    public ResponseEntity<ProblemDetail> handleIdentityProviderException(IdentityProviderException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("https://api.epm.com/errors/identity-provider-unavailable"));
        problem.setTitle("Identity Provider Unavailable");
        problem.setDetail(ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    /** 404 Not Found — invitation token does not exist. */
    @ExceptionHandler(InvitationTokenInvalidException.class)
    public ProblemDetail handleInvitationTokenInvalid(InvitationTokenInvalidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/invitation-not-found"));
        problem.setTitle("Invitation Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 410 Gone — invitation token has expired. */
    @ExceptionHandler(InvitationTokenExpiredException.class)
    public ProblemDetail handleInvitationTokenExpired(InvitationTokenExpiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.GONE);
        problem.setType(URI.create("https://api.epm.com/errors/invitation-expired"));
        problem.setTitle("Invitation Expired");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** 409 Conflict — invitation token already used. */
    @ExceptionHandler(InvitationTokenAlreadyUsedException.class)
    public ProblemDetail handleInvitationTokenAlreadyUsed(InvitationTokenAlreadyUsedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://api.epm.com/errors/invitation-already-used"));
        problem.setTitle("Invitation Already Used");
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

    /** 500 Internal Server Error — unexpected exception. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://api.epm.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
