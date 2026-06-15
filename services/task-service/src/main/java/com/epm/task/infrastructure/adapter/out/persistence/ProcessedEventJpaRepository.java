package com.epm.task.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ProcessedEventJpaEntity}.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    boolean existsByEventId(String eventId);

    /**
     * Deletes the claim row by its eventId via an explicit bulk DELETE.
     *
     * <p>{@link ProcessedEventJpaEntity} implements {@code Persistable} with
     * {@code isNew() == true} (so claim inserts force a {@code persist()} that surfaces the
     * duplicate-PK violation). That same flag makes {@code SimpleJpaRepository.delete()} a
     * no-op (it skips removal for entities it considers "new"). Compensation therefore uses
     * this explicit modifying query rather than {@code deleteById}, so the claim is actually
     * removed.
     *
     * @param eventId the claim row to delete
     * @return number of rows deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM ProcessedEventJpaEntity p WHERE p.eventId = :eventId")
    int deleteByEventId(@Param("eventId") String eventId);
}
