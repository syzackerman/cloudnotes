package com.cloudnotes.service;

import com.cloudnotes.config.StorageProperties;
import com.cloudnotes.domain.Attachment;
import com.cloudnotes.domain.Note;
import com.cloudnotes.domain.User;
import com.cloudnotes.dto.attachment.AttachmentResponse;
import com.cloudnotes.dto.attachment.DownloadUrlResponse;
import com.cloudnotes.exception.AttachmentNotFoundException;
import com.cloudnotes.exception.EmptyFileException;
import com.cloudnotes.exception.FileTooLargeException;
import com.cloudnotes.exception.InvalidFileNameException;
import com.cloudnotes.exception.NoteNotFoundException;
import com.cloudnotes.exception.StorageException;
import com.cloudnotes.exception.UnsupportedFileTypeException;
import com.cloudnotes.repository.AttachmentRepository;
import com.cloudnotes.repository.NoteRepository;
import com.cloudnotes.repository.UserRepository;
import com.cloudnotes.storage.FileStorageService;
import com.cloudnotes.storage.FileStorageService.PresignedDownloadUrl;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "application/pdf", "text/plain");

    private final AttachmentRepository attachmentRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final ApplicationMetrics metrics;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            NoteRepository noteRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            StorageProperties storageProperties,
            ApplicationMetrics metrics) {
        this.attachmentRepository = attachmentRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.storageProperties = storageProperties;
        this.metrics = metrics;
    }

    @Transactional
    public AttachmentResponse upload(UUID userId, UUID noteId, MultipartFile file) {
        Note note = findActiveOwnedNote(userId, noteId);
        validate(file);

        UUID attachmentId = UUID.randomUUID();
        String originalFilename = cleanFilename(file.getOriginalFilename());
        String storageKey = storageKey(userId, noteId, attachmentId, originalFilename);
        try {
            uploadToStorage(storageKey, file);
        } catch (RuntimeException ex) {
            metrics.attachmentUploadFailure();
            throw ex;
        }

        User user = userRepository.getReferenceById(userId);
        Attachment attachment = Attachment.builder()
                .id(attachmentId)
                .note(note)
                .user(user)
                .originalFilename(originalFilename)
                .storageKey(storageKey)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .build();

        try {
            AttachmentResponse response = AttachmentResponse.from(attachmentRepository.save(attachment));
            metrics.attachmentUploaded();
            return response;
        } catch (RuntimeException ex) {
            metrics.attachmentUploadFailure();
            cleanupAfterFailedMetadataSave(storageKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> findAll(UUID userId, UUID noteId) {
        findActiveOwnedNote(userId, noteId);
        return attachmentRepository.findAllByNoteIdAndUserIdOrderByCreatedAtAsc(noteId, userId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse createDownloadUrl(UUID userId, UUID noteId, UUID attachmentId) {
        findActiveOwnedNote(userId, noteId);
        Attachment attachment = attachmentRepository
                .findByIdAndNoteIdAndUserId(attachmentId, noteId, userId)
                .orElseThrow(AttachmentNotFoundException::new);
        PresignedDownloadUrl presignedUrl = fileStorageService.createPresignedDownloadUrl(
                attachment.getStorageKey(), attachment.getOriginalFilename());
        return new DownloadUrlResponse(presignedUrl.url(), presignedUrl.expiresAt());
    }

    @Transactional
    public void delete(UUID userId, UUID noteId, UUID attachmentId) {
        findActiveOwnedNote(userId, noteId);
        Attachment attachment = attachmentRepository
                .findByIdAndNoteIdAndUserId(attachmentId, noteId, userId)
                .orElseThrow(AttachmentNotFoundException::new);
        fileStorageService.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
    }

    @Transactional
    public void deleteAllForPermanentNoteDelete(Note note) {
        UUID noteId = note.getId();
        UUID userId = note.getUser().getId();
        List<Attachment> attachments = attachmentRepository.findAllByNoteIdAndUserIdOrderByCreatedAtAsc(noteId, userId);
        for (Attachment attachment : attachments) {
            fileStorageService.delete(attachment.getStorageKey());
        }
        attachmentRepository.deleteAll(attachments);
    }

    private Note findActiveOwnedNote(UUID userId, UUID noteId) {
        return noteRepository.findActiveByIdAndUserId(noteId, userId).orElseThrow(NoteNotFoundException::new);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new EmptyFileException();
        }
        if (file.getSize() > storageProperties.maxFileSize().toBytes()) {
            throw new FileTooLargeException();
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedFileTypeException();
        }
        cleanFilename(file.getOriginalFilename());
    }

    private void uploadToStorage(String storageKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.upload(storageKey, inputStream, file.getSize(), file.getContentType());
        } catch (IOException ex) {
            throw new StorageException("Could not read uploaded file", ex);
        }
    }

    private void cleanupAfterFailedMetadataSave(String storageKey) {
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ignored) {
            // Metadata save failure is the primary error. Orphan cleanup is best-effort and can be retried
            // operationally.
        }
    }

    private String storageKey(UUID userId, UUID noteId, UUID attachmentId, String filename) {
        return "users/%s/notes/%s/%s-%s".formatted(userId, noteId, attachmentId, filename);
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidFileNameException();
        }
        if (filename.length() > 255) {
            throw new InvalidFileNameException();
        }
        String normalized = Normalizer.normalize(filename, Normalizer.Form.NFKC).replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (basename.isBlank() || ".".equals(basename) || "..".equals(basename)) {
            throw new InvalidFileNameException();
        }
        String sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_");
        if (sanitized.isBlank() || sanitized.length() > 255) {
            throw new InvalidFileNameException();
        }
        return sanitized;
    }
}
