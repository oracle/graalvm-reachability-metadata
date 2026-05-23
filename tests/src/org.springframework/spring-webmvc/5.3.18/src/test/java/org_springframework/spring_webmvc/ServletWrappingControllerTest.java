/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletWrappingControllerTest {
    @Test
    void afterPropertiesSetInstantiatesAndInitializesWrappedServlet() throws Exception {
        RecordingServlet.reset();
        Properties initParameters = new Properties();
        initParameters.setProperty("mode", "test");
        initParameters.setProperty("feature", "enabled");

        ServletWrappingController controller = new ServletWrappingController();
        controller.setBeanName("recordingServletController");
        controller.setServletClass(RecordingServlet.class);
        controller.setInitParameters(initParameters);

        controller.afterPropertiesSet();
        try {
            assertThat(RecordingServlet.constructorCalls).isEqualTo(1);
            assertThat(RecordingServlet.initCalls).isEqualTo(1);
            assertThat(RecordingServlet.initializedServletName).isEqualTo("recordingServletController");
            assertThat(RecordingServlet.initializedMode).isEqualTo("test");
            assertThat(RecordingServlet.initParameterNames).containsExactlyInAnyOrder("mode", "feature");
        } finally {
            controller.destroy();
        }

        assertThat(RecordingServlet.destroyCalls).isEqualTo(1);
    }

    public static class RecordingServlet extends GenericServlet {
        private static int constructorCalls;
        private static int initCalls;
        private static int destroyCalls;
        private static String initializedServletName;
        private static String initializedMode;
        private static String[] initParameterNames;

        public RecordingServlet() {
            constructorCalls++;
        }

        static void reset() {
            constructorCalls = 0;
            initCalls = 0;
            destroyCalls = 0;
            initializedServletName = null;
            initializedMode = null;
            initParameterNames = new String[0];
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
            initCalls++;
            initializedServletName = config.getServletName();
            initializedMode = config.getInitParameter("mode");
            initParameterNames = toArray(config.getInitParameterNames());
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        }

        @Override
        public void destroy() {
            destroyCalls++;
        }

        private static String[] toArray(Enumeration<String> parameterNames) {
            return parameterNames == null ? new String[0] : Collections.list(parameterNames).toArray(String[]::new);
        }
    }
}
