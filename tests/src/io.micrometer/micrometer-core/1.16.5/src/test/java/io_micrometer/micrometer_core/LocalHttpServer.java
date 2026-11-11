/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.io.IOException;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LocalHttpServer implements AutoCloseable {

    final Server server;
    final NetworkTrafficServerConnector connector;

    public LocalHttpServer() {
        server = new Server(new QueuedThreadPool(8, 2));
        connector = new NetworkTrafficServerConnector(server, null, null, null, 1, 1,
                new HttpConnectionFactory());
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(30000);
        server.addConnector(connector);
        server.setStopTimeout(30000);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) throws IOException {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain");
                response.getWriter().write("hello metrics");
                baseRequest.setHandled(true);
            }
        });
    }

    public void start() throws Exception {
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + connector.getLocalPort() + "/greeting";
    }

    @Override
    public void close() throws Exception {
        server.stop();
        server.destroy();
    }
}
