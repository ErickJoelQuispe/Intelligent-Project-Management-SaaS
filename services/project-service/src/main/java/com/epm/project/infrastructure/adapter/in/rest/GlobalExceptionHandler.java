package com.epm.project.infrastructure.adapter.in.rest;

import java.net.URI;

import com.epm.project.domain.exception.DuplicateProjectMemberException;
import com.epm.project.domain.exception.DuplicateTeamAssignmentException;
import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
