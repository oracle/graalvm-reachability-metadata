/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.valves.FilterValve;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterValveTest {

    @Test
    void startsConfiguredServletFilter() throws Exception {
        RecordingFilter.reset();
        StandardService service = new StandardService();
        StandardEngine engine = new StandardEngine();
        engine.setName("filter-engine");
        service.setContainer(engine);
        StandardHost host = new StandardHost();
        host.setName("localhost");
        engine.addChild(host);
        StandardContext context = new StandardContext();
        context.setName("filter-valve-test");
        context.setPath("/filter-valve-test");
        context.setParent(host);
        FilterValve valve = new FilterValve();
        valve.setContainer(context);
        valve.setFilterClass(RecordingFilter.class.getName());
        valve.addInitParam("mode", "audit");

        try {
            valve.start();

            assertThat(RecordingFilter.initialized).isTrue();
            assertThat(RecordingFilter.mode).isEqualTo("audit");
        } finally {
            if (valve.getState().isAvailable()) {
                valve.stop();
            }
            if (valve.getState() != LifecycleState.DESTROYED) {
                valve.destroy();
            }
        }

        assertThat(RecordingFilter.destroyed).isTrue();
    }

    public static class RecordingFilter implements Filter {
        private static boolean initialized;
        private static boolean destroyed;
        private static String mode;

        private static void reset() {
            initialized = false;
            destroyed = false;
            mode = null;
        }

        @Override
        public void init(FilterConfig filterConfig) {
            initialized = true;
            mode = filterConfig.getInitParameter("mode");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
