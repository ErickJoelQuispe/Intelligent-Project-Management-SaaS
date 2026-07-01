package com.epm.auth.infrastructure.adapter.out.feign;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for user-service invitation endpoints.
 *
 * <p>Discovery is via Eureka (service name {@code "user-service"}).
 * Authentication is not required for the internal validate and mark-used endpoints
 * because they are accessible only within the service mesh (not exposed at the gateway).
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Validates an invitation token and returns the invitation details.
     *
     * @param token plaintext base64url invitation token
     * @return invitation details for the accept flow
     */
    @GetMapping("/api/v1/invitations/validate")
    ValidateInvitationResponse validateToken(@RequestParam("token") String token);

    /**
     * Marks an invitation as used so it cannot be accepted again.
     *
     * @param invitationId UUID of the invitation to mark used
     */
    @PostMapping("/api/v1/invitations/{invitationId}/use")
    void markUsed(@PathVariable UUID invitationId);
}
