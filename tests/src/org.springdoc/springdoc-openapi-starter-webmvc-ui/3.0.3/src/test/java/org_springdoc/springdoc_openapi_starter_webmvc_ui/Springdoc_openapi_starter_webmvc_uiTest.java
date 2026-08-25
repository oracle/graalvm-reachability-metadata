/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springdoc.springdoc_openapi_starter_webmvc_ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises Springdoc's MVC endpoints through a consumer-like Spring Boot application.
 *
 * §FS-test-contract.1.3
 */
@SpringBootTest(classes = Springdoc_openapi_starter_webmvc_uiTest.Application.class)
@AutoConfigureMockMvc
public class Springdoc_openapi_starter_webmvc_uiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesOpenApiDocumentForController() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/greeting'].get").exists());
    }

    @Test
    void redirectsSwaggerUiHomeToIndexPage() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void servesTransformedSwaggerUiIndexPage() throws Exception {
        String page = mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("Swagger UI");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(GreetingController.class)
    public static class Application {
    }

    @RestController
    public static class GreetingController {
        @GetMapping("/greeting")
        public String greeting() {
            return "hello";
        }
    }
}
