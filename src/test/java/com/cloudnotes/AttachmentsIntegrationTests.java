package com.cloudnotes;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudnotes.repository.AttachmentRepository;
import com.cloudnotes.repository.NoteRepository;
import com.cloudnotes.repository.TagRepository;
import com.cloudnotes.repository.UserRepository;
import com.cloudnotes.storage.FileStorageService;
import com.cloudnotes.storage.FileStorageService.PresignedDownloadUrl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:cloudnotes-attachments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "jwt.secret=test-only-secret-that-is-long-enough-for-hmac-signing",
            "jwt.expiration=PT1H",
            "cloudnotes.storage.max-file-size=10MB",
            "cloudnotes.rate-limit.enabled=false"
        })
@AutoConfigureMockMvc
class AttachmentsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FileStorageService fileStorageService;

    @BeforeEach
    void cleanDatabase() {
        attachmentRepository.deleteAll();
        noteRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
        reset(fileStorageService);
    }

    @Test
    void uploadsAllowedFile() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");

        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file("hello.txt", "text/plain", "hello".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.noteId").value(noteId))
                .andExpect(jsonPath("$.originalFilename").value("hello.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.sizeBytes").value(5))
                .andExpect(jsonPath("$.createdAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).upload(keyCaptor.capture(), any(InputStream.class), eq(5L), eq("text/plain"));
        String storageKey = keyCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(storageKey)
                .contains("/notes/" + noteId + "/")
                .startsWith("users/")
                .endsWith("-hello.txt");
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");

        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file("empty.txt", "text/plain", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        verify(fileStorageService, never()).upload(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");

        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file("script.js", "application/javascript", "alert(1)".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFileLargerThanTenMb() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");

        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file("large.txt", "text/plain", new byte[10 * 1024 * 1024 + 1]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void listsAttachments() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");
        upload(token, noteId, "a.txt", "text/plain", "a".getBytes());
        upload(token, noteId, "b.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(get("/api/notes/{noteId}/attachments", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("a.txt"))
                .andExpect(jsonPath("$[1].originalFilename").value("b.pdf"));
    }

    @Test
    void generatesDownloadUrl() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");
        String attachmentId = upload(token, noteId, "a.txt", "text/plain", "a".getBytes());
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(fileStorageService.createPresignedDownloadUrl(anyString(), eq("a.txt")))
                .thenReturn(new PresignedDownloadUrl("https://example.test/download", expiresAt));

        mockMvc.perform(get("/api/notes/{noteId}/attachments/{attachmentId}/download", noteId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.test/download"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void deletesAttachment() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");
        String attachmentId = upload(token, noteId, "a.txt", "text/plain", "a".getBytes());

        mockMvc.perform(delete("/api/notes/{noteId}/attachments/{attachmentId}", noteId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notes/{noteId}/attachments", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(fileStorageService).delete(contains("-a.txt"));
    }

    @Test
    void cannotUploadListDownloadOrDeleteAnotherUsersAttachments() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        String ownerNoteId = createNote(ownerToken, "Private note");
        String attachmentId = upload(ownerToken, ownerNoteId, "a.txt", "text/plain", "a".getBytes());

        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", ownerNoteId)
                        .file(file("other.txt", "text/plain", "x".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/notes/{noteId}/attachments", ownerNoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/notes/{noteId}/attachments/{attachmentId}/download", ownerNoteId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notes/{noteId}/attachments/{attachmentId}", ownerNoteId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotUploadToSoftDeletedNoteAndRestorePreservesAttachments() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");
        upload(token, noteId, "a.txt", "text/plain", "a".getBytes());

        mockMvc.perform(delete("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file("b.txt", "text/plain", "b".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/notes/{id}/restore", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/{noteId}/attachments", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("a.txt"));
    }

    @Test
    void permanentlyDeletingNoteTriggersAttachmentCleanup() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");
        upload(token, noteId, "a.txt", "text/plain", "a".getBytes());

        mockMvc.perform(delete("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/notes/{id}/permanent", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        verify(fileStorageService).delete(contains("-a.txt"));
        org.assertj.core.api.Assertions.assertThat(attachmentRepository.count()).isZero();
    }

    @Test
    void storageFailuresReturnAppropriateResponses() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Attachment note");

        doThrow(new com.cloudnotes.exception.StorageException("boom"))
                .when(fileStorageService)
                .upload(anyString(), any(InputStream.class), anyLong(), anyString());
        mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file("a.txt", "text/plain", "a".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadGateway());

        reset(fileStorageService);
        String attachmentId = upload(token, noteId, "a.txt", "text/plain", "a".getBytes());

        when(fileStorageService.createPresignedDownloadUrl(anyString(), anyString()))
                .thenThrow(new com.cloudnotes.exception.StorageException("boom"));
        mockMvc.perform(get("/api/notes/{noteId}/attachments/{attachmentId}/download", noteId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadGateway());

        reset(fileStorageService);
        doThrow(new com.cloudnotes.exception.StorageException("boom"))
                .when(fileStorageService)
                .delete(anyString());
        mockMvc.perform(delete("/api/notes/{noteId}/attachments/{attachmentId}", noteId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadGateway());
        org.assertj.core.api.Assertions.assertThat(
                        attachmentRepository.existsById(java.util.UUID.fromString(attachmentId)))
                .isTrue();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "displayName", "Attachment User",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private String createNote(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", title, "content", "Attachment content"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private String upload(String token, String noteId, String filename, String contentType, byte[] content)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/notes/{noteId}/attachments", noteId)
                        .file(file(filename, contentType, content))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
