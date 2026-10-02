/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.filters.RateLimitFilter;
import org.apache.catalina.util.FastRateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

public class RateLimitFilterTest {

    @Test
    void initializesConfiguredRateLimiter() {
        RateLimitFilter filter = new RateLimitFilter();
        filter.setRateLimitClassName(FastRateLimiter.class.getName());
        filter.setBucketDuration(30);
        filter.setBucketRequests(12);

        try {
            assertThatCode(() -> filter.init(new EmptyFilterConfig("rate-limit", newServletContext())))
                    .doesNotThrowAnyException();
        } finally {
            filter.destroy();
        }
    }

    private static ServletContext newServletContext() {
        StandardService service = new StandardService();
        StandardEngine engine = new StandardEngine();
        service.setContainer(engine);

        StandardHost host = new StandardHost();
        host.setName("localhost");
        engine.addChild(host);

        StandardContext context = new StandardContext();
        context.setName("rate-limit");
        context.setPath("/rate-limit");
        host.addChild(context);
        return context.getServletContext();
    }

    private record EmptyFilterConfig(String filterName, ServletContext servletContext) implements FilterConfig {
        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.emptyEnumeration();
        }
    }
}
