package com.inkflow.crm.config;

import com.inkflow.crm.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inkflow.openapi.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
class OpenApiDocumentGenerationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiSpecIsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void exportOpenApiSpecToDocs() throws Exception {
        if (!"true".equals(System.getProperty("generate.openapi"))) {
            return;
        }
        var result = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn();
        Path out = Path.of("docs/openapi.yaml");
        Files.createDirectories(out.getParent());
        Files.writeString(out, result.getResponse().getContentAsString());
    }
}
