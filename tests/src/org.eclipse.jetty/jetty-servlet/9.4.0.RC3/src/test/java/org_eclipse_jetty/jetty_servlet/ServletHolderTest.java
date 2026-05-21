/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.Test;

public class ServletHolderTest {
    @Test
    public void startMapsForcedJspPathToJspServlet() throws Exception {
        Server server = new Server();
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        ServletHolder jspServlet = new ServletHolder("jsp", CountingServlet.class);
        ServletHolder forcedJsp = new ServletHolder();
        forcedJsp.setName("monthlyReport");
        forcedJsp.setForcedPath("/WEB-INF/pages/monthly-report.jsp");
        context.addServlet(jspServlet, "*.jsp");
        context.addServlet(forcedJsp, "/reports/monthly");
        server.setHandler(context);

        try {
            server.start();

            assertThat(forcedJsp.getClassName()).isEqualTo(CountingServlet.class.getName());
            assertThat(forcedJsp.getInitParameter("jspFile")).isEqualTo("/WEB-INF/pages/monthly-report.jsp");
            assertThat(forcedJsp.getServlet()).isInstanceOf(CountingServlet.class);
        } finally {
            server.stop();
            server.destroy();
        }
    }

    @Test
    public void getServletCreatesServletInstanceFromHeldClass() throws ServletException {
        ServletHolder holder = new ServletHolder("counting", CountingServlet.class);
        holder.setServletHandler(new ServletHandler());

        Servlet servlet = holder.getServlet();

        assertThat(servlet).isInstanceOf(CountingServlet.class);
        assertThat(((CountingServlet) servlet).getServletConfig()).isNotNull();
    }

    public static class CountingServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private ServletConfig servletConfig;

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
            servletConfig = config;
        }

        @Override
        public ServletConfig getServletConfig() {
            return servletConfig;
        }
    }
}
