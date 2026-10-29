/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_proxy;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.proxy.AbstractProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class ProxyTestSupport {
    protected final AtomicInteger upstreamRequests = new AtomicInteger();
    protected final Server upstream = newServer();
    protected final Server proxy = newServer();
    protected final HttpClient client = new HttpClient(new HttpClientTransportOverHTTP(1));

    @BeforeEach
    void startUpstream() throws Exception {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new UpstreamServlet(upstreamRequests)), "/*");
        upstream.setHandler(context);
        upstream.start();
        client.setExecutor(new QueuedThreadPool(16, 2));
        client.setConnectTimeout(10000);
        client.setIdleTimeout(30000);
        client.setFollowRedirects(false);
    }

    protected void startProxy(AbstractProxyServlet servlet, Map<String, String> parameters, boolean forward) throws Exception {
        ServletHolder holder = new ServletHolder(servlet);
        holder.setAsyncSupported(true);
        holder.setInitParameter("timeout", "30000");
        holder.setInitParameter("idleTimeout", "30000");
        holder.setInitParameter("viaHost", "proxy-test");
        parameters.forEach(holder::setInitParameter);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(holder, "/*");
        proxy.setHandler(context);
        proxy.start();
        if (forward) {
            client.getProxyConfiguration().getProxies().add(new HttpProxy("127.0.0.1", port(proxy)));
        }
        client.start();
    }

    protected String upstreamAddress() {
        return "127.0.0.1:" + port(upstream);
    }

    protected String upstreamUrl(String path) {
        return "http://" + upstreamAddress() + path;
    }

    protected Request reverseRequest(String path) {
        return client.newRequest("http://127.0.0.1:" + port(proxy) + path).timeout(30, TimeUnit.SECONDS);
    }

    protected Request forwardRequest(String path) {
        return client.newRequest(upstreamUrl(path)).timeout(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void stopResources() throws Exception {
        try {
            client.stop();
        } finally {
            try {
                proxy.stop();
            } finally {
                upstream.stop();
            }
        }
    }

    private static Server newServer() {
        Server server = new Server(new QueuedThreadPool(32, 4));
        server.setStopTimeout(10000);
        ServerConnector connector = new ServerConnector(server, 1, 1);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(30000);
        server.addConnector(connector);
        return server;
    }

    private static int port(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    public static class UpstreamServlet extends HttpServlet {
        private final AtomicInteger requests;

        public UpstreamServlet(AtomicInteger requests) {
            this.requests = requests;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            requests.incrementAndGet();
            response.setHeader("X-Upstream-Method", request.getMethod());
            response.setHeader("X-Upstream-Path", request.getRequestURI());
            response.setHeader("X-Upstream-Query", request.getQueryString());
            response.setHeader("X-Upstream-Token", request.getHeader("X-Token"));
            response.setHeader("X-Upstream-Hop", request.getHeader("X-Hop"));
            response.setHeader("X-Upstream-Via", request.getHeader("Via"));
            response.setHeader("X-Upstream-Forwarded-For", request.getHeader("X-Forwarded-For"));
            if (request.getRequestURI().endsWith("/redirect")) {
                response.setStatus(HttpServletResponse.SC_FOUND);
                response.setHeader("Location", "/landing");
            } else if ("POST".equals(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.setContentType("application/octet-stream");
                request.getInputStream().transferTo(response.getOutputStream());
            } else {
                response.setContentType("text/plain");
                response.getWriter().write("upstream:" + request.getRequestURI());
            }
        }
    }
}
