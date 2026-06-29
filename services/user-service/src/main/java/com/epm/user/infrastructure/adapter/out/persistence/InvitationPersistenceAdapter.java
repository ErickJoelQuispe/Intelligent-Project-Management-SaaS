package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.out.InvitationRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link InvitationRepository} port using JPA.
 *
 * <p>Maps between the {@link Invitation} domain aggregate and {@link InvitationJpaEntity}.
 */
@Component
public class InvitationPersistenceAdapter implements InvitationRepository {

    private final InvitationJpaRepository invitationJpaRepository;

    public InvitationPersistenceAdapter(InvitationJpaRepository invitationJpaRepository) {
        this.invitationJpaRepository = invitationJpaRepository;
    }

    @Override
    public Invitation save(Invitation invitation) {
        InvitationJpaEntity entity = invitationJpaRepository.findById(invitation.getId())
                .orElseGet(InvitationJpaEntity::new);
        applyFields(entity, invitation);
        InvitationJpaEntity saved = invitationJpaRepository.save(entity);
        return reconstitute(saved);
    }

    @Override
    public Optional<Invitation> findByTokenHash(String tokenHash) {
        return invitationJpaRepository.findByTokenHash(tokenHash)
                .map(this::reconstitute);
    }

    @Override
    public Optional<Invitation> findById(UUID id) {
        return invitationJpaRepository.findById(id)
                .map(this::reconstitute);
    }

    @Override
    public boolean existsActiveInvitation(String email, UUID tenantId) {
        return invitationJpaRepository.existsByTenantIdAndEmailAndUsedAtIsNull(tenantId, email);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private void applyFields(InvitationJpaEntity entity, Invitation invitation) {
        entity.setId(invitation.getId());
        entity.setTeamId(invitation.getTeamId());
        entity.setTenantId(invitation.getTenantId());
        entity.setEmail(invitation.getEmail());
        entity.setTokenHash(invitation.getTokenHash());
        entity.setRole(invitation.getRole());
        entity.setExpiresAt(invitation.getExpiresAt());
        entity.setUsedAt(invitation.getUsedAt());
        entity.setCreatedBy(invitation.getCreatedBy());
        entity.setCreatedAt(invitation.getCreatedAt());
    }

    private Invitation reconstitute(InvitationJpaEntity entity) {
        return Invitation.reconstitute(
                entity.getId(),
                entity.getTeamId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getTokenHash(),
                entity.getRole(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getVersion());
    }
}
