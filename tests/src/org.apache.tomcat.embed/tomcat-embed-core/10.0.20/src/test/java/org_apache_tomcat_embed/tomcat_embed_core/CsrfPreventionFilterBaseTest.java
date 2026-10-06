/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.security.SecureRandom;
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
        filter.setRandomClass(SecureRandom.class.getName());
        filter.setDenyStatus(401);

        filter.init(new EmptyFilterConfig("csrf", newServletContext()));

        assertThat(filter.getDenyStatus()).isEqualTo(401);
    }

    private static ServletContext newServletContext() {
        StandardService service = new StandardService();
        StandardEngine engine = new StandardEngine();
        service.setContainer(engine);

        StandardHost host = new StandardHost();
        host.setName("localhost");
        engine.addChild(host);

        StandardContext context = new StandardContext();
        context.setName("csrf");
        context.setPath("/csrf");
        host.addChild(context);
        return context.getServletContext();
    }

    private record EmptyFilterConfig(String filterName, ServletContext servletContext) implements FilterConfig {
        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
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
