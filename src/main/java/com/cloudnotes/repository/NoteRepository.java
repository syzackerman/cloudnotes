package com.cloudnotes.repository;

import com.cloudnotes.domain.Note;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    @Query(
            """
            select distinct n
            from Note n
            left join n.tags t
            where n.user.id = :userId
              and n.deleted = false
              and (:favorite is null or n.favorite = :favorite)
              and (:tag is null or t.normalizedName = :tag)
            """)
    Page<Note> findActiveByUserId(
            @Param("userId") UUID userId,
            @Param("favorite") Boolean favorite,
            @Param("tag") String tag,
            Pageable pageable);

    @Query(
            """
            select distinct n
            from Note n
            left join n.tags t
            where n.user.id = :userId
              and n.deleted = false
              and (lower(n.title) like concat('%', :q, '%') escape '!'
                   or lower(coalesce(n.content, '')) like concat('%', :q, '%') escape '!')
              and (:favorite is null or n.favorite = :favorite)
              and (:tag is null or t.normalizedName = :tag)
            """)
    Page<Note> searchActiveByUserId(
            @Param("userId") UUID userId,
            @Param("q") String q,
            @Param("favorite") Boolean favorite,
            @Param("tag") String tag,
            Pageable pageable);

    @Query(
            """
            select n
            from Note n
            where n.user.id = :userId
              and n.deleted = true
            """)
    Page<Note> findDeletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "tags")
    @Query(
            """
            select n
            from Note n
            where n.id = :id
              and n.user.id = :userId
              and n.deleted = false
            """)
    Optional<Note> findActiveByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = "tags")
    @Query(
            """
            select n
            from Note n
            where n.id = :id
              and n.user.id = :userId
              and n.deleted = true
            """)
    Optional<Note> findDeletedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = "tags")
    @Query(
            """
            select n
            from Note n
            where n.id = :id
              and n.user.id = :userId
            """)
    Optional<Note> findAnyByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
