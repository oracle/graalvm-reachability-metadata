/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultServletTest {
    @TempDir
    Path resourceBase;

    @Test
    public void initLoadsDefaultDirectoryStylesheet() throws Exception {
        Server server = new Server();
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.setResourceBase(resourceBase.toUri().toASCIIString());
        ServletHolder holder = context.addServlet(DefaultServlet.class, "/");
        server.setHandler(context);

        try {
            server.start();

            Servlet servlet = holder.getServlet();
            assertThat(servlet).isInstanceOf(DefaultServlet.class);
            assertThat(servlet.getServletConfig()).isNotNull();
        } finally {
            server.stop();
            server.destroy();
        }
    }
}
