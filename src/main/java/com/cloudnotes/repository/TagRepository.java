package com.cloudnotes.repository;

import com.cloudnotes.domain.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByUserIdOrderByNameAsc(UUID userId);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    List<Tag> findAllByIdInAndUserId(Collection<UUID> ids, UUID userId);

    boolean existsByUserIdAndNormalizedName(UUID userId, String normalizedName);

    @Modifying
    @Query(value = "delete from note_tags where tag_id = :tagId", nativeQuery = true)
    void deleteNoteRelationships(@Param("tagId") UUID tagId);
}
