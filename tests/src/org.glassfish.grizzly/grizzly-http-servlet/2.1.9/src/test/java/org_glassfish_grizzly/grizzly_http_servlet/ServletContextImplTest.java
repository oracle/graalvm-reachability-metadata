/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import javax.servlet.ServletContext;

import org.glassfish.grizzly.servlet.ServletContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletContextImplTest {
    @Test
    void resourceLookupsDelegateToContextClassLoader() throws Exception {
        ServletContext context = new ServletContextImpl();

        assertThat(context.getResource("/missing-grizzly-servlet-context-resource.txt")).isNull();
        assertThat(context.getResourceAsStream("/missing-grizzly-servlet-context-resource.txt")).isNull();
    }
}
