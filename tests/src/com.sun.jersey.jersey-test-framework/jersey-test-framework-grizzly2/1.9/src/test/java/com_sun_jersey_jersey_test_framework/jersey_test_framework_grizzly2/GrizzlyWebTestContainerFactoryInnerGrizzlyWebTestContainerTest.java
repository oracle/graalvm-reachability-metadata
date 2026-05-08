/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey_jersey_test_framework.jersey_test_framework_grizzly2;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.grizzly2.web.GrizzlyWebTestContainerFactory;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;

public class GrizzlyWebTestContainerFactoryInnerGrizzlyWebTestContainerTest {
    private static final AtomicInteger SERVLET_INSTANCE_COUNT = new AtomicInteger();
    private static final AtomicInteger FILTER_INSTANCE_COUNT = new AtomicInteger();

    @Test
    public void createsConfiguredServletAndFilterInstances() {
        SERVLET_INSTANCE_COUNT.set(0);
        FILTER_INSTANCE_COUNT.set(0);

        WebAppDescriptor descriptor = new WebAppDescriptor.Builder()
                .servletClass(CountingServlet.class)
                .addFilter(CountingFilter.class, "counting-filter", Collections.singletonMap("filter-param", "value"))
                .contextPath("coverage")
                .servletPath("api")
                .initParam("servlet-param", "value")
                .contextParam("context-param", "value")
                .build();
        GrizzlyWebTestContainerFactory factory = new GrizzlyWebTestContainerFactory();

        TestContainer container = null;
        try {
            container = factory.create(URI.create("http://127.0.0.1:0/"), descriptor);

            assertThat(container.getBaseUri().getPath()).isEqualTo("/coverage/api");
            assertThat(SERVLET_INSTANCE_COUNT.get()).isEqualTo(1);
            assertThat(FILTER_INSTANCE_COUNT.get()).isEqualTo(1);
        } finally {
            if (container != null) {
                container.stop();
            }
        }
    }

    public static class CountingServlet extends HttpServlet {
        public CountingServlet() {
            SERVLET_INSTANCE_COUNT.incrementAndGet();
        }
    }

    public static class CountingFilter implements Filter {
        public CountingFilter() {
            FILTER_INSTANCE_COUNT.incrementAndGet();
        }

        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }
}
