package com.cloudnotes;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudnotes.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:cloudnotes-auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void registersUserSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "  SOPHIA@example.COM  ",
                                "displayName", "Sophia",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("sophia@example.com"))
                .andExpect(jsonPath("$.displayName").value("Sophia"))
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registrationWithoutAuthorizationHeaderReturnsCreatedToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "sophia@example.com",
                                "displayName", "Sophia",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(blankOrNullString())));
    }

    @Test
    void registersUserWithoutJwtEvenWhenAuthorizationHeaderIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "sophia@example.com",
                                "displayName", "Sophia",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("sophia@example.com"))
                .andExpect(jsonPath("$.token", not(blankOrNullString())));
    }

    @Test
    void rejectsDuplicateRegistration() throws Exception {
        register("sophia@example.com", "correct-horse-battery");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", " SOPHIA@example.com ",
                                "displayName", "Sophia Again",
                                "password", "another-good-password"))))
                .andExpect(status().isConflict());
    }

    @Test
    void logsInSuccessfully() throws Exception {
        register("sophia@example.com", "correct-horse-battery");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", " SOPHIA@example.com ",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("sophia@example.com"))
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logsInWithoutJwtEvenWhenAuthorizationHeaderIsInvalid() throws Exception {
        register("sophia@example.com", "correct-horse-battery");

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "sophia@example.com",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sophia@example.com"))
                .andExpect(jsonPath("$.token", not(blankOrNullString())));
    }

    @Test
    void rejectsInvalidLoginCredentials() throws Exception {
        register("sophia@example.com", "correct-horse-battery");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "sophia@example.com",
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserRejectsInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtCanAccessCurrentUser() throws Exception {
        String token = register("sophia@example.com", "correct-horse-battery");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("sophia@example.com"))
                .andExpect(jsonPath("$.displayName").value("Sophia"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private String register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "displayName", "Sophia",
                                "password", password))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}
