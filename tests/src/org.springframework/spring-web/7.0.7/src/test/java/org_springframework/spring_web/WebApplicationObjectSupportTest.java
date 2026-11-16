/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.support.WebApplicationObjectSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class WebApplicationObjectSupportTest {

    @Test
    void initializesContextAwareBeanAndReadsServletRelativeResource() throws Exception {
        MockServletContext servletContext = new MockServletContext("classpath:webroot");
        servletContext.setContextPath("/catalog");
        try (GenericWebApplicationContext context = new GenericWebApplicationContext(servletContext)) {
            context.registerBean("catalog", Catalog.class);
            context.refresh();
            Catalog catalog = context.getBean("catalog", Catalog.class);
            assertThat(catalog.contextPath()).isEqualTo("/catalog");
            assertThat(catalog.banner()).isEqualTo("catalog ready\n");
        }
    }

    public static final class Catalog extends WebApplicationObjectSupport {
        public String contextPath() {
            return getServletContext().getContextPath();
        }

        public String banner() throws Exception {
            Resource resource = getWebApplicationContext().getResource("/banner.txt");
            try (InputStream input = resource.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
