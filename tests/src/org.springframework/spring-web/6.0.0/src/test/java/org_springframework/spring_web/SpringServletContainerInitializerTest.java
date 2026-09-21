/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockServletContext;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringServletContainerInitializerTest {

    @Test
    void onStartupInstantiatesDetectedWebApplicationInitializer() throws Exception {
        RecordingWebApplicationInitializer.reset();
        MockServletContext servletContext = new MockServletContext();
        SpringServletContainerInitializer initializer = new SpringServletContainerInitializer();

        initializer.onStartup(Set.of(RecordingWebApplicationInitializer.class), servletContext);

        assertThat(RecordingWebApplicationInitializer.startupCount()).isEqualTo(1);
        assertThat(RecordingWebApplicationInitializer.servletContext()).isSameAs(servletContext);
    }

    public static final class RecordingWebApplicationInitializer implements WebApplicationInitializer {

        private static final AtomicInteger STARTUP_COUNT = new AtomicInteger();

        private static final AtomicReference<ServletContext> SERVLET_CONTEXT = new AtomicReference<>();

        public RecordingWebApplicationInitializer() {
        }

        @Override
        public void onStartup(ServletContext servletContext) throws ServletException {
            STARTUP_COUNT.incrementAndGet();
            SERVLET_CONTEXT.set(servletContext);
        }

        static void reset() {
            STARTUP_COUNT.set(0);
            SERVLET_CONTEXT.set(null);
        }

        static int startupCount() {
            return STARTUP_COUNT.get();
        }

        static ServletContext servletContext() {
            return SERVLET_CONTEXT.get();
        }
    }
}
