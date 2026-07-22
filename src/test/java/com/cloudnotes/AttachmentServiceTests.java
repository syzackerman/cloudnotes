package com.cloudnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudnotes.config.StorageProperties;
import com.cloudnotes.domain.Attachment;
import com.cloudnotes.domain.Note;
import com.cloudnotes.domain.User;
import com.cloudnotes.dto.attachment.AttachmentResponse;
import com.cloudnotes.repository.AttachmentRepository;
import com.cloudnotes.repository.NoteRepository;
import com.cloudnotes.repository.UserRepository;
import com.cloudnotes.service.ApplicationMetrics;
import com.cloudnotes.service.AttachmentService;
import com.cloudnotes.storage.FileStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTests {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    private final StorageProperties storageProperties =
            new StorageProperties("us-east-1", "bucket", Duration.ofMinutes(5), DataSize.ofMegabytes(10));

    @Test
    void uploadSanitizesFilenameBeforeBuildingStorageKey() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("owner@example.com")
                .displayName("Owner")
                .passwordHash("hash")
                .build();
        Note note = Note.builder().id(noteId).user(user).title("Note").build();
        MockMultipartFile file = new MockMultipartFile("file", "../My File.txt", "text/plain", "hello".getBytes());
        AttachmentService service = new AttachmentService(
                attachmentRepository,
                noteRepository,
                userRepository,
                fileStorageService,
                storageProperties,
                new ApplicationMetrics(new SimpleMeterRegistry()));

        when(noteRepository.findActiveByIdAndUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttachmentResponse response = service.upload(userId, noteId, file);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).upload(keyCaptor.capture(), any(InputStream.class), anyLong(), eq("text/plain"));
        assertThat(keyCaptor.getValue())
                .startsWith("users/" + userId + "/notes/" + noteId + "/")
                .endsWith("-My_File.txt");
        assertThat(response.originalFilename()).isEqualTo("My_File.txt");
    }
}
