package com.cloudnotes.repository;

import com.cloudnotes.domain.Attachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findAllByNoteIdAndUserIdOrderByCreatedAtAsc(UUID noteId, UUID userId);

    Optional<Attachment> findByIdAndNoteIdAndUserId(UUID id, UUID noteId, UUID userId);
}
