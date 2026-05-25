/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

public class ServletContextHandlerTest {
    @Test
    public void getSecurityHandlerCreatesDefaultSecurityHandlerWhenSecurityIsEnabled() {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SECURITY);

        SecurityHandler securityHandler = context.getSecurityHandler();

        assertThat(securityHandler).isInstanceOf(ConstraintSecurityHandler.class);
        assertThat(context.getHandler()).isSameAs(securityHandler);
    }
}
