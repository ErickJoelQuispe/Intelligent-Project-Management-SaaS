package com.epm.auth.domain.port.in.command;

/**
 * Command for the accept-invitation use case.
 *
 * @param token     plaintext base64url token from the invitation link
 * @param firstName invited user's first name
 * @param lastName  invited user's last name
 * @param password  raw password chosen by the invited user
 */
public record AcceptInvitationCommand(
        String token,
        String firstName,
        String lastName,
        String password) {
}
