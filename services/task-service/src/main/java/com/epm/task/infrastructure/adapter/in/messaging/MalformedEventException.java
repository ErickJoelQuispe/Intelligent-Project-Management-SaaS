package com.epm.task.infrastructure.adapter.in.messaging;

/**
 * Signals that an inbound Kafka event could not be parsed or is missing a required field.
 *
 * <p>This is a <strong>poison-message</strong> marker: the payload is structurally invalid
 * (bad JSON, missing {@code eventId}, unparseable UUID, missing required field, etc.) and
 * will never succeed on retry. Consumers catch it explicitly to skip the message instead of
 * retrying forever. It is deliberately distinct from a bare {@link NullPointerException} so
 * that an NPE raised by business logic is NOT mistaken for a malformed payload.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
