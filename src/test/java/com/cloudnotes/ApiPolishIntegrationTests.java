package com.cloudnotes;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudnotes.repository.NoteRepository;
import com.cloudnotes.repository.TagRepository;
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
            "spring.datasource.url=jdbc:h2:mem:cloudnotes-polish;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "jwt.secret=test-only-secret-that-is-long-enough-for-hmac-signing",
            "jwt.expiration=PT1H",
            "cloudnotes.rate-limit.enabled=false",
            "cloudnotes.cors.allowed-origins=http://localhost:5173",
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.endpoint.prometheus.enabled=true",
            "management.prometheus.metrics.export.enabled=true"
        })
@AutoConfigureMockMvc
class ApiPolishIntegrationTests {

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
    void openApiIsAvailableAndDocumentsJwtScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CloudNotes API"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                        .value("JWT"))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/notes'].get.security[0].bearerAuth")
                        .exists());
    }

    @Test
    void swaggerUiIsAvailableInDevelopmentConfiguration() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void validationErrorsUseConsistentBodyWithRequestIdAndFieldErrors() throws Exception {
        String token = register("owner@example.com");

        mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("X-Request-ID", "req-validation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "", "content", "Body"))))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-ID", "req-validation-1"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/notes"))
                .andExpect(jsonPath("$.requestId").value("req-validation-1"))
                .andExpect(jsonPath("$.fieldErrors.title", not(blankOrNullString())))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void authenticationErrorsUseConsistentBody() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", not(blankOrNullString())))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Authentication is required or the token is invalid"));
    }

    @Test
    void invalidTokenUsesConsistentBodyWithoutJwtDetails() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Authentication is required or the token is invalid"))
                .andExpect(jsonPath("$.message").value(not(startsWith("JWT"))));
    }

    @Test
    void requestIdIsGeneratedAcceptedOrReplaced() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-ID", "safe-request-id_123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "safe-request-id_123"));

        mockMvc.perform(get("/actuator/health").header("X-Request-ID", "bad request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", not("bad request")))
                .andExpect(header().string("X-Request-ID", not(blankOrNullString())));
    }

    @Test
    void paginationLimitsAndSortFieldsAreValidated() throws Exception {
        String token = register("owner@example.com");

        mockMvc.perform(get("/api/notes?size=101").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Page size must not exceed 100"));

        mockMvc.perform(get("/api/notes?sort=passwordHash,asc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: passwordHash"));
    }

    @Test
    void corsAllowsConfiguredOriginAndDeniesUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/notes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-ID"));

        mockMvc.perform(options("/api/notes")
                        .header(HttpHeaders.ORIGIN, "https://unknown.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void prometheusEndpointExistsOnlyWhenEnabledAndStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "displayName", "Owner",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
