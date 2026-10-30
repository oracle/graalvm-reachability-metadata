/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.ResourceBundleViewResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResourceBundleViewResolverTest {
    @Test
    void resolvesAndRendersLocalizedViewsFromResourceBundles() throws Exception {
        MockServletContext servletContext = new MockServletContext();
        try (GenericWebApplicationContext context = new GenericWebApplicationContext()) {
            context.setServletContext(servletContext);
            context.refresh();

            ResourceBundleViewResolver resolver = new ResourceBundleViewResolver();
            resolver.setApplicationContext(context);
            resolver.setServletContext(servletContext);
            resolver.setBasename("org_springframework.spring_webmvc.localized_views");
            try {
                resolver.afterPropertiesSet();
                View defaultView = resolver.resolveViewName("welcome", Locale.ROOT);
                assertNotNull(defaultView);
                assertSame(defaultView, resolver.resolveViewName("welcome", Locale.ROOT));

                MockHttpServletRequest defaultRequest = new MockHttpServletRequest(servletContext);
                MockHttpServletResponse defaultResponse = new MockHttpServletResponse();
                defaultView.render(Map.of("name", "Ada"), defaultRequest, defaultResponse);
                assertEquals("/WEB-INF/views/welcome.jsp", defaultResponse.getForwardedUrl());
                assertEquals("Ada", defaultRequest.getAttribute("name"));

                View frenchView = resolver.resolveViewName("welcome", Locale.FRENCH);
                assertNotNull(frenchView);
                MockHttpServletRequest frenchRequest = new MockHttpServletRequest(servletContext);
                MockHttpServletResponse frenchResponse = new MockHttpServletResponse();
                frenchView.render(Map.of("name", "Grace"), frenchRequest, frenchResponse);
                assertEquals("/WEB-INF/views/bienvenue.jsp", frenchResponse.getForwardedUrl());
                assertEquals("Grace", frenchRequest.getAttribute("name"));
                assertNull(resolver.resolveViewName("unknown", Locale.FRENCH));
            } finally {
                resolver.destroy();
            }
        }
    }
}
