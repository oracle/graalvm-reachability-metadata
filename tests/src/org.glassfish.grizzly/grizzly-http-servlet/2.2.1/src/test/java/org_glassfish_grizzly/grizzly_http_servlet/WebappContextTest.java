/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappContextTest {
    private static final String RESOURCE_PATH = "/org_glassfish_grizzly/grizzly_http_servlet/webapp-context-resource.txt";

    @Test
    void deployCreatesClassRegisteredListenersServletsAndNamedFilters() {
        LifecycleListener.reset();
        StartupServlet.reset();
        NamedFilter.reset();

        WebappContext context = new WebappContext("dynamic-components", "/dynamic-components");
        context.addListener(LifecycleListener.class);

        ServletRegistration servletRegistration = context.addServlet("startupServlet", StartupServlet.class);
        servletRegistration.setLoadOnStartup(0);
        servletRegistration.addMapping("/startup");

        FilterRegistration filterRegistration = context.addFilter("namedFilter", NamedFilter.class.getName());
        filterRegistration.setInitParameter("filter-marker", "loaded-by-name");
        filterRegistration.addMappingForUrlPatterns(null, "/startup");

        HttpServer server = new HttpServer();
        try {
            context.deploy(server);

            assertThat(LifecycleListener.events()).containsExactly("constructed", "initialized:/dynamic-components");
            assertThat(StartupServlet.instances()).hasValue(1);
            assertThat(StartupServlet.initCalls()).hasValue(1);
            assertThat(NamedFilter.instances()).hasValue(1);
            assertThat(NamedFilter.initMarkers()).containsExactly("loaded-by-name");
        } finally {
            context.undeploy();
        }

        assertThat(NamedFilter.destroyCalls()).hasValue(1);
        assertThat(LifecycleListener.events()).endsWith("destroyed:/dynamic-components");
    }

    @Test
    void resourceLookupsUseContextClassLoader() throws Exception {
        WebappContext context = new WebappContext("resources", "/resources");

        URL resource = context.getResource(RESOURCE_PATH);
        assertThat(resource).isNotNull();
        assertThat(resource.toString()).contains("webapp-context-resource.txt");

        try (InputStream input = context.getResourceAsStream(RESOURCE_PATH)) {
            assertThat(input).isNotNull();
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("resource loaded through WebappContext");
        }
    }

    public static final class LifecycleListener implements ServletContextListener {
        private static final CopyOnWriteArrayList<String> EVENTS = new CopyOnWriteArrayList<>();

        public LifecycleListener() {
            EVENTS.add("constructed");
        }

        private static void reset() {
            EVENTS.clear();
        }

        private static CopyOnWriteArrayList<String> events() {
            return EVENTS;
        }

        @Override
        public void contextInitialized(ServletContextEvent event) {
            EVENTS.add("initialized:" + event.getServletContext().getContextPath());
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
            EVENTS.add("destroyed:" + event.getServletContext().getContextPath());
        }
    }

    public static final class StartupServlet extends HttpServlet {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();

        public StartupServlet() {
            INSTANCES.incrementAndGet();
        }

        private static void reset() {
            INSTANCES.set(0);
            INIT_CALLS.set(0);
        }

        private static AtomicInteger instances() {
            return INSTANCES;
        }

        private static AtomicInteger initCalls() {
            return INIT_CALLS;
        }

        @Override
        public void init() {
            INIT_CALLS.incrementAndGet();
        }
    }

    public static final class NamedFilter implements Filter {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger DESTROY_CALLS = new AtomicInteger();
        private static final CopyOnWriteArrayList<String> INIT_MARKERS = new CopyOnWriteArrayList<>();

        public NamedFilter() {
            INSTANCES.incrementAndGet();
        }

        private static void reset() {
            INSTANCES.set(0);
            DESTROY_CALLS.set(0);
            INIT_MARKERS.clear();
        }

        private static AtomicInteger instances() {
            return INSTANCES;
        }

        private static AtomicInteger destroyCalls() {
            return DESTROY_CALLS;
        }

        private static CopyOnWriteArrayList<String> initMarkers() {
            return INIT_MARKERS;
        }

        @Override
        public void init(FilterConfig filterConfig) {
            INIT_MARKERS.add(filterConfig.getInitParameter("filter-marker"));
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            DESTROY_CALLS.incrementAndGet();
        }
    }
}
