package com.epm.ai.infrastructure.adapter.in.rest;

import java.net.URI;

import com.epm.ai.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and infrastructure exceptions to RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class AiControllerAdvice {

    /** 404 Not Found — project not found. */
    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleProjectNotFound(ProjectNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://api.epm.com/errors/project-not-found"));
        problem.setTitle("Project Not Found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "PROJECT_NOT_FOUND");
        return problem;
    }

    /**
     * 503 Service Unavailable — circuit breaker open (AI model unavailable).
     *
     * <p>Sets {@code Retry-After} header per RFC 7231 §7.1.3.
     */
    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitBreakerOpen(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("https://api.epm.com/errors/ai-service-unavailable"));
        problem.setTitle("AI Service Unavailable");
        problem.setDetail("AI service is temporarily unavailable. Please try again later.");
        problem.setProperty("errorCode", "AI_SERVICE_UNAVAILABLE");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "30");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).headers(headers).body(problem);
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

    /** 405 Method Not Allowed — wrong HTTP method for the endpoint. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        problem.setType(URI.create("https://api.epm.com/errors/method-not-allowed"));
        problem.setTitle("Method Not Allowed");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errorCode", "METHOD_NOT_ALLOWED");
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
        org.slf4j.LoggerFactory.getLogger(AiControllerAdvice.class)
                .error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://api.epm.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
