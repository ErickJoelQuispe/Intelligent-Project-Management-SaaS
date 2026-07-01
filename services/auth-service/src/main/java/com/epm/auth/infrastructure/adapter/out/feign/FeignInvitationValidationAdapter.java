package com.epm.auth.infrastructure.adapter.out.feign;

import java.util.UUID;

import com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException;
import com.epm.auth.domain.exception.InvitationTokenExpiredException;
import com.epm.auth.domain.exception.InvitationTokenInvalidException;
import com.epm.auth.domain.port.out.InvitationValidationPort;
import com.epm.auth.domain.port.out.InvitationValidationResult;
import feign.FeignException;
import org.springframework.stereotype.Component;

/**
 * Implements {@link InvitationValidationPort} by delegating to {@link UserServiceClient}.
 *
 * <p>Maps Feign HTTP error statuses to domain exceptions:
 * <ul>
 *   <li>404 → {@link InvitationTokenInvalidException}</li>
 *   <li>410 → {@link InvitationTokenExpiredException}</li>
 *   <li>409 → {@link InvitationTokenAlreadyUsedException}</li>
 * </ul>
 */
@Component
public class FeignInvitationValidationAdapter implements InvitationValidationPort {

    private final UserServiceClient userServiceClient;

    public FeignInvitationValidationAdapter(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public InvitationValidationResult validateToken(String token) {
        try {
            ValidateInvitationResponse response = userServiceClient.validateToken(token);
            return new InvitationValidationResult(
                    response.invitationId(),
                    response.email(),
                    response.tenantId(),
                    response.teamId(),
                    response.role());
        } catch (FeignException.NotFound ex) {
            throw new InvitationTokenInvalidException("Invitation token not found");
        } catch (FeignException.Gone ex) {
            throw new InvitationTokenExpiredException("Invitation token has expired");
        } catch (FeignException.Conflict ex) {
            throw new InvitationTokenAlreadyUsedException("Invitation token already used");
        }
    }

    @Override
    public void markUsed(UUID invitationId) {
        userServiceClient.markUsed(invitationId);
    }
}
