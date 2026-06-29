package com.epm.user.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.epm.user.domain.exception.InvitationAlreadyUsedException;
import com.epm.user.domain.exception.InvitationExpiredException;
import com.epm.user.domain.exception.InvitationNotFoundException;
import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.in.ValidateInvitationUseCase;
import com.epm.user.domain.port.out.InvitationRepository;

/**
 * Implementation of {@link ValidateInvitationUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class ValidateInvitationUseCaseImpl implements ValidateInvitationUseCase {

    private final InvitationRepository invitationRepository;

    public ValidateInvitationUseCaseImpl(InvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    @Override
    public Invitation validateInvitation(String plaintextToken) {
        String hash = sha256hex(plaintextToken);
        Invitation invitation = invitationRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found for given token"));

        if (invitation.isExpired()) {
            throw new InvitationExpiredException(invitation.getId());
        }
        if (invitation.getUsedAt() != null) {
            throw new InvitationAlreadyUsedException(invitation.getId());
        }

        return invitation;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String sha256hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
