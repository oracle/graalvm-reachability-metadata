/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.filters.CsrfPreventionFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CsrfPreventionFilterBaseTest {

    @Test
    void initializesConfiguredRandomSource() throws Exception {
        CsrfPreventionFilter filter = new CsrfPreventionFilter();

        filter.init(new EmptyFilterConfig());
        try {
            assertThat(filter.getDenyStatus()).isEqualTo(403);
        } finally {
            filter.destroy();
        }
    }

    private static final class EmptyFilterConfig implements FilterConfig {

        @Override
        public String getFilterName() {
            return "csrf";
        }

        @Override
        public ServletContext getServletContext() {
            StandardService service = new StandardService();
            StandardEngine engine = new StandardEngine();
            service.setContainer(engine);
            StandardHost host = new StandardHost();
            host.setParent(engine);
            StandardContext context = new StandardContext();
            context.setParent(host);
            return context.getServletContext();
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
