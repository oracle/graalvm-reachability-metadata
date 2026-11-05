/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_servlet;

import groovy.servlet.TemplateServlet;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TemplateServletTest {
    @Test
    void initializesConfiguredTemplateEngineInsteadOfDefault() throws Exception {
        RecordingServletContext context = new RecordingServletContext();
        MockServletConfig config = new MockServletConfig(context, "templates");
        config.addInitParameter("template.engine", "groovy.text.GStringTemplateEngine");
        TemplateServlet servlet = new TemplateServlet();

        try {
            servlet.init(config);

            assertSame(config, servlet.getServletConfig());
            assertTrue(context.messages.contains(
                    "templates: Servlet groovy.servlet.TemplateServlet initialized on class groovy.text.GStringTemplateEngine"),
                    () -> "Expected successful initialization with the configured engine: " + context.messages);
        } finally {
            servlet.destroy();
        }
    }

    private static class RecordingServletContext extends MockServletContext {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}
