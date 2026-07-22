package com.cloudnotes.service;

import com.cloudnotes.domain.Note;
import com.cloudnotes.domain.Tag;
import com.cloudnotes.domain.User;
import com.cloudnotes.dto.note.CreateNoteRequest;
import com.cloudnotes.dto.note.FavoriteNoteRequest;
import com.cloudnotes.dto.note.NoteResponse;
import com.cloudnotes.dto.note.UpdateNoteRequest;
import com.cloudnotes.dto.note.UpdateNoteTagsRequest;
import com.cloudnotes.exception.NoteNotFoundException;
import com.cloudnotes.repository.NoteRepository;
import com.cloudnotes.repository.TagRepository;
import com.cloudnotes.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;
    private final ApplicationMetrics metrics;

    public NoteService(
            NoteRepository noteRepository,
            TagRepository tagRepository,
            UserRepository userRepository,
            AttachmentService attachmentService,
            ApplicationMetrics metrics) {
        this.noteRepository = noteRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
        this.metrics = metrics;
    }

    @Transactional
    public NoteResponse create(UUID userId, CreateNoteRequest request) {
        User user = userRepository.getReferenceById(userId);
        Note note = Note.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .build();

        NoteResponse response = NoteResponse.from(noteRepository.save(note));
        metrics.noteCreated();
        return response;
    }

    @Transactional(readOnly = true)
    public Page<NoteResponse> findAll(UUID userId, String query, Boolean favorite, String tag, Pageable pageable) {
        String normalizedQuery = normalizeSearchQuery(query);
        String normalizedTag = normalizeTagFilter(tag);
        Page<Note> notes = normalizedQuery == null
                ? noteRepository.findActiveByUserId(userId, favorite, normalizedTag, pageable)
                : noteRepository.searchActiveByUserId(userId, normalizedQuery, favorite, normalizedTag, pageable);
        return notes.map(NoteResponse::from);
    }

    @Transactional(readOnly = true)
    public NoteResponse findById(UUID userId, UUID noteId) {
        return noteRepository
                .findActiveByIdAndUserId(noteId, userId)
                .map(NoteResponse::from)
                .orElseThrow(NoteNotFoundException::new);
    }

    @Transactional
    public NoteResponse update(UUID userId, UUID noteId, UpdateNoteRequest request) {
        Note note = noteRepository.findActiveByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setUpdatedAt(Instant.now());

        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse updateFavorite(UUID userId, UUID noteId, FavoriteNoteRequest request) {
        Note note = noteRepository.findActiveByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
        note.setFavorite(request.favorite());
        note.setUpdatedAt(Instant.now());

        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse replaceTags(UUID userId, UUID noteId, UpdateNoteTagsRequest request) {
        Note note = noteRepository.findActiveByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
        Set<UUID> uniqueTagIds = new LinkedHashSet<>(request.tagIds());
        List<Tag> tags =
                uniqueTagIds.isEmpty() ? List.of() : tagRepository.findAllByIdInAndUserId(uniqueTagIds, userId);
        if (tags.size() != uniqueTagIds.size()) {
            throw new NoteNotFoundException();
        }

        note.getTags().clear();
        note.getTags().addAll(tags);
        note.setUpdatedAt(Instant.now());

        return NoteResponse.from(note);
    }

    @Transactional
    public void softDelete(UUID userId, UUID noteId) {
        Note note = noteRepository.findActiveByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
        Instant now = Instant.now();
        note.setDeleted(true);
        note.setDeletedAt(now);
        note.setUpdatedAt(now);
        metrics.noteDeleted();
    }

    @Transactional(readOnly = true)
    public Page<NoteResponse> findTrash(UUID userId, Pageable pageable) {
        return noteRepository.findDeletedByUserId(userId, pageable).map(NoteResponse::from);
    }

    @Transactional
    public NoteResponse restore(UUID userId, UUID noteId) {
        Note note = noteRepository.findDeletedByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
        note.setDeleted(false);
        note.setDeletedAt(null);
        note.setUpdatedAt(Instant.now());
        metrics.noteRestored();

        return NoteResponse.from(note);
    }

    @Transactional
    public void permanentDelete(UUID userId, UUID noteId) {
        Note note = noteRepository.findDeletedByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
        attachmentService.deleteAllForPermanentNoteDelete(note);
        noteRepository.delete(note);
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private String normalizeTagFilter(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return TagService.normalizeName(tag);
    }
}
