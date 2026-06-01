package com.epm.auth.domain.port.in.command;

/**
 * Command: data required to register a new account.
 *
 * <p>Plain Java record — no framework annotations.
 */
public record RegisterAccountCommand(
        String email,
        String password,
        String firstName,
        String lastName) {}
