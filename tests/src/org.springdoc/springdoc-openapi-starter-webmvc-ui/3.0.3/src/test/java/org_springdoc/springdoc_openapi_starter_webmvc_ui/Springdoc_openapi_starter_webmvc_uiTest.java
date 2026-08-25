/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springdoc.springdoc_openapi_starter_webmvc_ui;

import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.ui.SwaggerUiHome;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Springdoc's Swagger UI MVC controller without Spring Test's generated AOT context.
 *
 * §FS-test-contract.1.3
 */
public class Springdoc_openapi_starter_webmvc_uiTest {
    @Test
    void buildsSwaggerUiRedirect() {
        String redirect = new SwaggerUiHome().index();

        assertThat(redirect).startsWith("redirect:");
    }
}
