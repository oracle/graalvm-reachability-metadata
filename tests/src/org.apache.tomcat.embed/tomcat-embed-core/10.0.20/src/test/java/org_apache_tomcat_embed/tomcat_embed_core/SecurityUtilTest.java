/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.catalina.security.SecurityUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityUtilTest {

    @Test
    void invokesServletLifecycleMethodsThroughPublicSecurityApi() throws Exception {
        RecordingServlet servlet = new RecordingServlet();

        SecurityUtil.doAsPrivilege("init", servlet, new Class<?>[] {ServletConfig.class}, new Object[] {null});
        SecurityUtil.doAsPrivilege("destroy", servlet);

        assertThat(servlet.getInitCalls()).isEqualTo(1);
        assertThat(servlet.getDestroyCalls()).isEqualTo(1);
    }

    public static final class RecordingServlet implements Servlet {
        private int initCalls;
        private int destroyCalls;

        @Override
        public void init(ServletConfig config) throws ServletException {
            initCalls++;
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
            destroyCalls++;
        }

        private int getInitCalls() {
            return initCalls;
        }

        private int getDestroyCalls() {
            return destroyCalls;
        }
    }
}
