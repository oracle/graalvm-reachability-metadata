/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.HttpJspBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpJspBaseTest {
    @Test
    void initializesJspFactoryAndDelegatesGeneratedJspLifecycleMethods() throws Exception {
        final JspFactory previousFactory = JspFactory.getDefaultFactory();
        JspFactory.setDefaultFactory(null);

        try {
            final TestJspPage page = new TestJspPage();

            page.init(new TestServletConfig());
            page.service(null, null);
            page.destroy();

            assertThat(page.getServletInfo()).contains("Jasper");
            assertThat(page.events).isEqualTo("jspInit,_jspInit,_jspService,jspDestroy,_jspDestroy");
        } finally {
            JspFactory.setDefaultFactory(previousFactory);
        }
    }

    private static final class TestJspPage extends HttpJspBase {
        private String events = "";

        @Override
        public void jspInit() {
            record("jspInit");
        }

        @Override
        public void _jspInit() {
            record("_jspInit");
        }

        @Override
        public void jspDestroy() {
            record("jspDestroy");
        }

        @Override
        protected void _jspDestroy() {
            record("_jspDestroy");
        }

        @Override
        public void _jspService(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            record("_jspService");
        }

        private void record(final String event) {
            if (events.isEmpty()) {
                events = event;
            } else {
                events += "," + event;
            }
        }
    }

    private static final class TestServletConfig implements ServletConfig {
        @Override
        public String getServletName() {
            return "http-jsp-base-test";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(final String name) {
            return null;
        }

        @Override
        public Enumeration getInitParameterNames() {
            return Collections.enumeration(Collections.emptyList());
        }
    }
}
