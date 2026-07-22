package com.cloudnotes;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudnotes.repository.NoteRepository;
import com.cloudnotes.repository.TagRepository;
import com.cloudnotes.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:cloudnotes-notes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "jwt.secret=test-only-secret-that-is-long-enough-for-hmac-signing",
            "jwt.expiration=PT1H",
            "cloudnotes.rate-limit.enabled=false"
        })
@AutoConfigureMockMvc
class NotesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        noteRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createsNote() throws Exception {
        String token = register("owner@example.com");

        mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "First note",
                                "content", "Hello notes"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("First note"))
                .andExpect(jsonPath("$.content").value("Hello notes"))
                .andExpect(jsonPath("$.createdAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.updatedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void listsNotesForAuthenticatedUser() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        createNote(ownerToken, "Owner note", "Mine");
        createNote(otherToken, "Other note", "Not mine");

        mockMvc.perform(get("/api/notes").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Owner note"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getsOwnNote() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Readable", "Owned content");

        mockMvc.perform(get("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noteId))
                .andExpect(jsonPath("$.title").value("Readable"))
                .andExpect(jsonPath("$.content").value("Owned content"));
    }

    @Test
    void updatesOwnNote() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Old title", "Old content");

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "New title",
                                "content", "New content"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noteId))
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.content").value("New content"));
    }

    @Test
    void deletesOwnNote() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Disposable", "Gone soon");

        mockMvc.perform(delete("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAccessAnotherUsersNote() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        String ownerNoteId = createNote(ownerToken, "Private", "Owner only");

        mockMvc.perform(get("/api/notes/{id}", ownerNoteId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/notes/{id}", ownerNoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Stolen",
                                "content", "Nope"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notes/{id}", ownerNoteId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginationWorks() throws Exception {
        String token = register("owner@example.com");
        createNote(token, "Alpha", "One");
        createNote(token, "Bravo", "Two");
        createNote(token, "Charlie", "Three");

        mockMvc.perform(get("/api/notes")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .queryParam("sort", "title,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Charlie"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void favoriteAndUnfavoriteNote() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Favorite candidate", "Useful");

        mockMvc.perform(patch("/api/notes/{id}/favorite", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("favorite", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(patch("/api/notes/{id}/favorite", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("favorite", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(false));
    }

    @Test
    void createsAndListsTags() throws Exception {
        String token = register("owner@example.com");

        createTag(token, " Work ");
        createTag(token, "Personal");

        mockMvc.perform(get("/api/tags").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Personal"))
                .andExpect(jsonPath("$[1].name").value("Work"));
    }

    @Test
    void duplicateTagNamesAreRejectedCaseInsensitively() throws Exception {
        String token = register("owner@example.com");
        createTag(token, " Work ");

        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "work"))))
                .andExpect(status().isConflict());
    }

    @Test
    void deletesOwnTagAndRemovesItFromNotes() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Tagged", "Has a tag");
        String tagId = createTag(token, "Work");
        assignTags(token, noteId, List.of(tagId)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/tags/{id}", tagId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tags").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void assignsAndReplacesNoteTags() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Tagged", "Has tags");
        String workTagId = createTag(token, "Work");
        String personalTagId = createTag(token, "Personal");

        assignTags(token, noteId, List.of(workTagId, personalTagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.tags[0].name").value("Personal"))
                .andExpect(jsonPath("$.tags[1].name").value("Work"));

        assignTags(token, noteId, List.of(personalTagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.tags[0].name").value("Personal"));
    }

    @Test
    void cannotUseAnotherUsersTag() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        String ownerNoteId = createNote(ownerToken, "Owned", "Mine");
        String otherTagId = createTag(otherToken, "Other");

        assignTags(ownerToken, ownerNoteId, List.of(otherTagId)).andExpect(status().isNotFound());
    }

    @Test
    void searchesTitleAndContent() throws Exception {
        String token = register("owner@example.com");
        createNote(token, "Meeting notes", "Agenda and actions");
        createNote(token, "Random", "Project agenda");
        createNote(token, "Unrelated", "Nothing here");

        mockMvc.perform(get("/api/notes").queryParam("q", "meeting").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Meeting notes"));

        mockMvc.perform(get("/api/notes")
                        .queryParam("q", "AGENDA")
                        .queryParam("sort", "title,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Meeting notes"))
                .andExpect(jsonPath("$.content[1].title").value("Random"));
    }

    @Test
    void searchTreatsSqlWildcardsAsLiteralText() throws Exception {
        String token = register("owner@example.com");
        createNote(token, "100% literal", "Contains a percent sign");
        createNote(token, "Plain note", "Would match if percent were treated as a wildcard");

        mockMvc.perform(get("/api/notes").queryParam("q", "%").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("100% literal"));
    }

    @Test
    void filtersByFavoriteTagAndCombinedFilters() throws Exception {
        String token = register("owner@example.com");
        String workTagId = createTag(token, "Work");
        String personalTagId = createTag(token, "Personal");
        String projectNoteId = createNote(token, "Project plan", "Quarterly roadmap");
        String meetingNoteId = createNote(token, "Project meeting", "Follow up");
        String personalNoteId = createNote(token, "Project grocery", "Buy milk");

        assignTags(token, projectNoteId, List.of(workTagId));
        assignTags(token, meetingNoteId, List.of(workTagId));
        assignTags(token, personalNoteId, List.of(personalTagId));
        setFavorite(token, projectNoteId, true);
        setFavorite(token, personalNoteId, true);

        mockMvc.perform(get("/api/notes")
                        .queryParam("favorite", "true")
                        .queryParam("sort", "title,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Project grocery"))
                .andExpect(jsonPath("$.content[1].title").value("Project plan"));

        mockMvc.perform(get("/api/notes")
                        .queryParam("tag", "work")
                        .queryParam("sort", "title,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Project meeting"))
                .andExpect(jsonPath("$.content[1].title").value("Project plan"));

        mockMvc.perform(get("/api/notes")
                        .queryParam("q", "project")
                        .queryParam("tag", "WORK")
                        .queryParam("favorite", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Project plan"));
    }

    @Test
    void softDeleteTrashRestoreAndPermanentDelete() throws Exception {
        String token = register("owner@example.com");
        String noteId = createNote(token, "Trash me", "Temporary");

        mockMvc.perform(delete("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/notes/trash").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(noteId));

        mockMvc.perform(post("/api/notes/{id}/restore", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noteId));

        mockMvc.perform(get("/api/notes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(delete("/api/notes/{id}", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/notes/{id}/permanent", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/notes/{id}/permanent", noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersCannotAccessAnotherUsersDeletedNotes() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        String ownerNoteId = createNote(ownerToken, "Private trash", "Still private");

        mockMvc.perform(delete("/api/notes/{id}", ownerNoteId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notes/trash").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(post("/api/notes/{id}/restore", ownerNoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notes/{id}/permanent", ownerNoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "displayName", "Notes User",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private String createNote(String token, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "content", content))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private String createTag(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private ResultActions assignTags(String token, String noteId, List<String> tagIds) throws Exception {
        return mockMvc.perform(put("/api/notes/{id}/tags", noteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("tagIds", tagIds))));
    }

    private void setFavorite(String token, String noteId, boolean favorite) throws Exception {
        mockMvc.perform(patch("/api/notes/{id}/favorite", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("favorite", favorite))))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
