/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;

import org.springframework.web.servlet.mvc.ServletWrappingController;

public class ServletWrappingControllerTest {
    @Test
    public void afterPropertiesSetCreatesAndInitializesWrappedServlet() throws Exception {
        RecordingServlet.reset();
        Properties initParameters = new Properties();
        initParameters.setProperty("message", "hello");

        ServletWrappingController controller = new ServletWrappingController();
        controller.setBeanName("recordingServlet");
        controller.setServletClass(RecordingServlet.class);
        controller.setInitParameters(initParameters);

        controller.afterPropertiesSet();

        ServletConfig config = RecordingServlet.config.get();
        assertThat(RecordingServlet.instances).hasValue(1);
        assertThat(config).isNotNull();
        assertThat(config.getServletName()).isEqualTo("recordingServlet");
        assertThat(config.getInitParameter("message")).isEqualTo("hello");
        assertThat(Collections.list(config.getInitParameterNames())).containsExactly("message");

        controller.destroy();

        assertThat(RecordingServlet.destroyed).isTrue();
    }

    public static class RecordingServlet implements Servlet {
        static final AtomicInteger instances = new AtomicInteger();
        static final AtomicReference<ServletConfig> config = new AtomicReference<>();
        static final AtomicBoolean destroyed = new AtomicBoolean();

        public RecordingServlet() {
            instances.incrementAndGet();
        }

        static void reset() {
            instances.set(0);
            config.set(null);
            destroyed.set(false);
        }

        @Override
        public void init(ServletConfig servletConfig) {
            config.set(servletConfig);
        }

        @Override
        public ServletConfig getServletConfig() {
            return config.get();
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        }

        @Override
        public String getServletInfo() {
            return "recording";
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }
    }
}
