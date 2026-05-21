/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.glassfish.grizzly.servlet.ServletConfigImpl;
import org.glassfish.grizzly.servlet.ServletHandler;
import org.glassfish.grizzly.servlet.WebappContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletHandlerTest {
    @Test
    void loadServletInstantiatesServletFromConfiguredClass() throws Exception {
        ClassInstantiatedServlet.reset();
        TestServletConfig servletConfig = new TestServletConfig(new WebappContext("handler-context", "/handler"));
        servletConfig.setServletName("classInstantiatedServlet");
        TestServletHandler servletHandler = new TestServletHandler(servletConfig);
        servletHandler.useServletClass(ClassInstantiatedServlet.class);

        servletHandler.initializeServlet();

        assertThat(ClassInstantiatedServlet.constructorCalls()).hasValue(1);
        assertThat(ClassInstantiatedServlet.initCalls()).hasValue(1);
        assertThat(servletHandler.getServletInstance()).isInstanceOf(ClassInstantiatedServlet.class);
        ClassInstantiatedServlet servlet = (ClassInstantiatedServlet) servletHandler.getServletInstance();
        assertThat(servlet.servletName()).isEqualTo("classInstantiatedServlet");
        assertThat(servlet.contextPath()).isEqualTo("/handler");
    }

    private static final class TestServletConfig extends ServletConfigImpl {
        private TestServletConfig(WebappContext servletContext) {
            super(servletContext);
        }
    }

    private static final class TestServletHandler extends ServletHandler {
        private TestServletHandler(ServletConfigImpl servletConfig) {
            super(servletConfig);
        }

        private void useServletClass(Class<? extends HttpServlet> servletClass) {
            setServletClass(servletClass);
        }

        private void initializeServlet() throws ServletException {
            loadServlet();
        }
    }

    public static final class ClassInstantiatedServlet extends HttpServlet {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();
        private String servletName;
        private String contextPath;

        public ClassInstantiatedServlet() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        private static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            INIT_CALLS.set(0);
        }

        private static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }

        private static AtomicInteger initCalls() {
            return INIT_CALLS;
        }

        private String servletName() {
            return servletName;
        }

        private String contextPath() {
            return contextPath;
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
            INIT_CALLS.incrementAndGet();
            servletName = config.getServletName();
            contextPath = config.getServletContext().getContextPath();
        }
    }
}
