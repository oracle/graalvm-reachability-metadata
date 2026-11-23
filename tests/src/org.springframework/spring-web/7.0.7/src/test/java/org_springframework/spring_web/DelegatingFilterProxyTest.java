/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.FormContentFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatingFilterProxyTest {

    @Test
    void configuresProxyFromServletInitParametersAndDelegatesToManagedFilter() throws Exception {
        MockServletContext servletContext = new MockServletContext();
        try (GenericWebApplicationContext context = new GenericWebApplicationContext(servletContext)) {
            context.registerBean("formDecoder", FormContentFilter.class);
            context.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
            MockFilterConfig configuration = new MockFilterConfig(servletContext, "formProxy");
            configuration.addInitParameter("targetBeanName", "formDecoder");
            configuration.addInitParameter("targetFilterLifecycle", "true");
            DelegatingFilterProxy proxy = new DelegatingFilterProxy();
            proxy.init(configuration);
            try {
                MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "PUT", "/items");
                request.setContentType("application/x-www-form-urlencoded");
                request.setContent("name=blue+sky".getBytes(StandardCharsets.UTF_8));
                MockHttpServletResponse response = new MockHttpServletResponse();
                proxy.doFilter(request, response, (incoming, outgoing) -> {
                    assertThat(((HttpServletRequest) incoming).getParameter("name")).isEqualTo("blue sky");
                    outgoing.getWriter().write("updated");
                });
                assertThat(response.getContentAsString()).isEqualTo("updated");
                assertThat(context.getBean("formDecoder", FormContentFilter.class).getFilterConfig())
                        .isSameAs(configuration);
            } finally {
                proxy.destroy();
            }
        }
    }
}
