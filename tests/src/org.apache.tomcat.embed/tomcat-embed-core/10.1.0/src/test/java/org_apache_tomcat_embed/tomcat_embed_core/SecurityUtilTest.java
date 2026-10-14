/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.catalina.security.SecurityUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityUtilTest {

    @Test
    void invokesServletLifecycleMethodWithContainerPrivileges() throws Exception {
        RecordingServlet servlet = new RecordingServlet();

        SecurityUtil.doAsPrivilege("destroy", servlet);

        assertThat(servlet.destroyed).isTrue();
    }

    private static final class RecordingServlet extends GenericServlet {
        private boolean destroyed;

        @Override
        public void service(ServletRequest request, ServletResponse response) {
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
