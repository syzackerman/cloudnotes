package com.cloudnotes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.profiles.active=prod",
            "spring.datasource.url=jdbc:h2:mem:cloudnotes-prod-docs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "jwt.secret=test-only-secret-that-is-long-enough-for-hmac-signing",
            "jwt.expiration=PT1H",
            "AWS_REGION=us-east-1",
            "AWS_S3_BUCKET=test-bucket",
            "cloudnotes.rate-limit.enabled=false"
        })
@AutoConfigureMockMvc
class SwaggerProductionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerAndOpenApiAreDisabledByDefaultInProductionProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
