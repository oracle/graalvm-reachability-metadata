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

import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.Test;

public class ServletHolderTest {
    private static final String JSP_PATH = "/WEB-INF/pages/monthly-report.jsp";
    private static final String PRECOMPILED_JSP_NAME = "org.apache.jsp.javaPackage:/WEB-INF/pages"
            + ".javaIdentifier:monthly-report.jsp";

    @Test
    public void startMapsForcedJspToPrecompiledServletClassUsingAvailableJasperUtility() throws Exception {
        ServletHandler handler = new ServletHandler();
        handler.setEnsureDefaultServlet(false);
        ServletHolder precompiledJsp = new ServletHolder(PRECOMPILED_JSP_NAME, CountingServlet.class);
        ServletHolder holder = new ServletHolder();
        holder.setName("monthly-report");
        holder.setForcedPath(JSP_PATH);

        handler.addServlet(precompiledJsp);
        handler.addServlet(holder);

        try {
            handler.start();

            assertThat(holder.getClassName()).isEqualTo(CountingServlet.class.getName());
        } finally {
            if (handler.isRunning()) {
                handler.stop();
            }
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
