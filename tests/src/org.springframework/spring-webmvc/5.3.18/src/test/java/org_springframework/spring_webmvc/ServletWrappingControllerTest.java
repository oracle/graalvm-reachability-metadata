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
import java.util.List;
import java.util.Properties;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.ServletWrappingController;

public class ServletWrappingControllerTest {
    @Test
    void afterPropertiesSetCreatesAndInitializesConfiguredServlet() throws Exception {
        RecordingServlet.reset();

        Properties initParameters = new Properties();
        initParameters.setProperty("mode", "test");
        initParameters.setProperty("feature", "wrapping");

        ServletWrappingController controller = new ServletWrappingController();
        controller.setBeanName("recordingServletBean");
        controller.setServletClass(RecordingServlet.class);
        controller.setInitParameters(initParameters);

        try {
            controller.afterPropertiesSet();

            assertThat(RecordingServlet.constructedCount).isEqualTo(1);
            assertThat(RecordingServlet.initializedCount).isEqualTo(1);
            assertThat(RecordingServlet.servletName).isEqualTo("recordingServletBean");
            assertThat(RecordingServlet.modeParameter).isEqualTo("test");
            assertThat(RecordingServlet.initParameterNames).containsExactlyInAnyOrder("feature", "mode");
        } finally {
            controller.destroy();
            RecordingServlet.reset();
        }
    }
}

final class RecordingServlet implements Servlet {
    static int constructedCount;
    static int initializedCount;
    static String servletName;
    static String modeParameter;
    static List<String> initParameterNames;

    RecordingServlet() {
        constructedCount++;
    }

    static void reset() {
        constructedCount = 0;
        initializedCount = 0;
        servletName = null;
        modeParameter = null;
        initParameterNames = List.of();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        initializedCount++;
        servletName = config.getServletName();
        modeParameter = config.getInitParameter("mode");
        initParameterNames = Collections.list(config.getInitParameterNames());
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
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
    }
}
