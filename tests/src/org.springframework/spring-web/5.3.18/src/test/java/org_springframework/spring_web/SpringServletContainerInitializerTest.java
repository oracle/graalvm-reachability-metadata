/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.util.Collections;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockServletContext;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringServletContainerInitializerTest {
    @AfterEach
    void resetInitializerState() {
        RecordingWebApplicationInitializer.started = false;
        RecordingWebApplicationInitializer.servletContext = null;
    }

    @Test
    void instantiatesAndStartsDiscoveredWebApplicationInitializer() throws ServletException {
        MockServletContext servletContext = new MockServletContext();
        SpringServletContainerInitializer initializer = new SpringServletContainerInitializer();

        initializer.onStartup(
                Collections.singleton(RecordingWebApplicationInitializer.class),
                servletContext);

        assertThat(RecordingWebApplicationInitializer.started).isTrue();
        assertThat(RecordingWebApplicationInitializer.servletContext).isSameAs(servletContext);
    }

    public static class RecordingWebApplicationInitializer implements WebApplicationInitializer {
        static boolean started;
        static ServletContext servletContext;

        @Override
        public void onStartup(ServletContext servletContext) {
            RecordingWebApplicationInitializer.started = true;
            RecordingWebApplicationInitializer.servletContext = servletContext;
        }
    }
}
