package com.epm.user.domain.exception;

/**
 * Thrown when a concurrent update is detected via version mismatch.
 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
