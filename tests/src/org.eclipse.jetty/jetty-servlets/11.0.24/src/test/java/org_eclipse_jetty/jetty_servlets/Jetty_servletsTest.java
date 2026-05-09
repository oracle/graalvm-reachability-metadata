/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CloseableDoSFilter;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.servlets.DoSFilter;
import org.eclipse.jetty.servlets.EventSource;
import org.eclipse.jetty.servlets.EventSourceServlet;
import org.eclipse.jetty.servlets.HeaderFilter;
import org.eclipse.jetty.servlets.IncludeExcludeBasedFilter;
import org.eclipse.jetty.servlets.QoSFilter;
import org.junit.jupiter.api.Test;

public class Jetty_servletsTest {
    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    void crossOriginFilterHandlesPreflightAndSimpleRequests() throws Exception {
        AtomicInteger servletCalls = new AtomicInteger();
        Servlet servlet = new TextServlet("application response", servletCalls);
        FilterHolder cors = new FilterHolder(new CrossOriginFilter());
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_TIMING_ORIGINS_PARAM, "https://timing.example.test");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Test,X-Requested-With");
        cors.setInitParameter(CrossOriginFilter.EXPOSED_HEADERS_PARAM, "X-Result");
        cors.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");

        try (JettyServer server = JettyServer.start(servlet, cors)) {
            Response preflight = rawRequest(
                server.uri("/resource"),
                "OPTIONS",
                Map.of(
                    "Origin", "https://api.example.test",
                    CrossOriginFilter.ACCESS_CONTROL_REQUEST_METHOD_HEADER, "PUT",
                    CrossOriginFilter.ACCESS_CONTROL_REQUEST_HEADERS_HEADER, "X-Test"),
                null);

            assertThat(preflight.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(preflight.header(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER))
                .isEqualTo("https://api.example.test");
            assertThat(preflight.header(CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER))
                .isEqualTo("true");
            assertThat(preflight.header(CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER))
                .contains("GET", "PUT");
            assertThat(preflight.header(CrossOriginFilter.ACCESS_CONTROL_ALLOW_HEADERS_HEADER))
                .contains("X-Test");
            assertThat(preflight.header("Vary")).contains("Origin");

            Response simple = rawRequest(
                server.uri("/resource"),
                "GET",
                Map.of("Origin", "https://timing.example.test"),
                null);

            assertThat(simple.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(simple.body()).isEqualTo("application response");
            assertThat(simple.header(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER))
                .isEqualTo("https://timing.example.test");
            assertThat(simple.header(CrossOriginFilter.TIMING_ALLOW_ORIGIN_HEADER))
                .isEqualTo("https://timing.example.test");
            assertThat(simple.header(CrossOriginFilter.ACCESS_CONTROL_EXPOSE_HEADERS_HEADER))
                .isEqualTo("X-Result");
        }
    }

    @Test
    void headerFilterAppliesConfiguredHeadersOnlyToIncludedRequests() throws Exception {
        FilterHolder headerFilter = new FilterHolder(new HeaderFilter());
        headerFilter.setInitParameter("headerConfig", "set X-Filtered: yes, add X-Multi: one, add X-Multi: two");
        headerFilter.setInitParameter("includedPaths", "/api/*");
        headerFilter.setInitParameter("excludedHttpMethods", "POST");

        try (JettyServer server = JettyServer.start(new TextServlet("ok", new AtomicInteger()), headerFilter)) {
            Response included = request(server.uri("/api/data.txt"), "GET", Map.of(), null);
            assertThat(included.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(included.header("X-Filtered")).isEqualTo("yes");
            assertThat(included.header("X-Multi")).contains("one", "two");

            Response excludedByMethod = request(server.uri("/api/data.txt"), "POST", Map.of(), "payload");
            assertThat(excludedByMethod.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(excludedByMethod.header("X-Filtered")).isNull();

            Response excludedByPath = request(server.uri("/plain/data.txt"), "GET", Map.of(), null);
            assertThat(excludedByPath.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(excludedByPath.header("X-Filtered")).isNull();
        }
    }

    @Test
    void eventSourceServletStreamsNamedEventsDataAndComments() throws Exception {
        ServletHolder holder = new ServletHolder(new GreetingEventSourceServlet());
        holder.setInitParameter("heartBeatPeriod", "30");

        try (JettyServer server = JettyServer.start(holder)) {
            Response response = request(
                server.uri("/events"),
                "GET",
                Map.of("Accept", "text/event-stream"),
                null);

            assertThat(response.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.header("Content-Type")).startsWith("text/event-stream");
            assertThat(response.body()).contains(
                "event: greeting\r\n",
                "data: hello\r\n",
                "data: world\r\n",
                ": completed\r\n");
        }
    }

    @Test
    void includeExcludeBasedFilterMatchesGuessedMimeTypes() throws Exception {
        FilterHolder mimeFilter = new FilterHolder(new MimeTypeFlaggingFilter());
        mimeFilter.setInitParameter("includedMimeTypes", "application/json,text/plain");
        mimeFilter.setInitParameter("excludedMimeTypes", "text/html");

        try (JettyServer server = JettyServer.start(new TextServlet("ok", new AtomicInteger()), mimeFilter)) {
            Response json = rawRequest(server.uri("/documents/report.json"), "GET", Map.of(), null);
            assertThat(json.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(json.header("X-Mime-Eligible")).isEqualTo("true");

            Response html = rawRequest(server.uri("/documents/page.html"), "GET", Map.of(), null);
            assertThat(html.status()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(html.header("X-Mime-Eligible")).isEqualTo("false");
        }
    }

    @Test
    void doSFilterExposesConfigurationWhitelistAndListenerActions() throws Exception {
        DoSFilter filter = new DoSFilter();
        Map<String, String> parameters = Map.ofEntries(
            Map.entry("maxRequestsPerSec", "3"),
            Map.entry("delayMs", "0"),
            Map.entry("throttledRequests", "2"),
            Map.entry("maxWaitMs", "125"),
            Map.entry("throttleMs", "250"),
            Map.entry("maxRequestMs", "500"),
            Map.entry("maxIdleTrackerMs", "750"),
            Map.entry("insertHeaders", "false"),
            Map.entry("remotePort", "true"),
            Map.entry("enabled", "true"),
            Map.entry("tooManyCode", "503"),
            Map.entry("ipWhitelist", "127.0.0.1,10.0.0.0/24"));

        try {
            filter.init(new SimpleFilterConfig("configuredDoS", parameters));

            assertThat(filter.getName()).isEqualTo("configuredDoS");
            assertThat(filter.getMaxRequestsPerSec()).isEqualTo(3);
            assertThat(filter.getDelayMs()).isZero();
            assertThat(filter.getThrottledRequests()).isEqualTo(2);
            assertThat(filter.getMaxWaitMs()).isEqualTo(125);
            assertThat(filter.getThrottleMs()).isEqualTo(250);
            assertThat(filter.getMaxRequestMs()).isEqualTo(500);
            assertThat(filter.getMaxIdleTrackerMs()).isEqualTo(750);
            assertThat(filter.isInsertHeaders()).isFalse();
            assertThat(filter.isRemotePort()).isTrue();
            assertThat(filter.isEnabled()).isTrue();
            assertThat(filter.getTooManyCode()).isEqualTo(503);
            assertThat(filter.getWhitelist()).contains("127.0.0.1", "10.0.0.0/24");
            assertThat(filter.addWhitelistAddress("192.168.1.10")).isTrue();
            assertThat(filter.removeWhitelistAddress("127.0.0.1")).isTrue();
            assertThat(filter.getWhitelist()).contains("192.168.1.10").doesNotContain("127.0.0.1");
            assertThat(DoSFilter.Action.fromDelay(-1)).isEqualTo(DoSFilter.Action.REJECT);
            assertThat(DoSFilter.Action.fromDelay(0)).isEqualTo(DoSFilter.Action.THROTTLE);
            assertThat(DoSFilter.Action.fromDelay(1)).isEqualTo(DoSFilter.Action.DELAY);
        } finally {
            filter.destroy();
        }
    }

    @Test
    void closeableDoSFilterClosesTimedOutRequests() throws Exception {
        FilterHolder filter = new FilterHolder(new CloseableDoSFilter());
        filter.setInitParameter("maxRequestMs", "100");
        filter.setInitParameter("maxRequestsPerSec", "100");
        filter.setInitParameter("insertHeaders", "false");
        filter.setAsyncSupported(true);

        try (JettyServer server = JettyServer.start(new SlowServlet(), filter);
             Socket socket = new Socket()) {
            URI slowUri = server.uri("/slow");
            socket.connect(new InetSocketAddress(slowUri.getHost(), slowUri.getPort()), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);

            String request = "GET /slow HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + slowUri.getPort() + "\r\n"
                + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();

            String response;
            try {
                ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
                socket.getInputStream().transferTo(responseBytes);
                response = responseBytes.toString(StandardCharsets.ISO_8859_1);
            } catch (SocketException e) {
                response = "";
            }

            assertThat(response).isEmpty();
        }
    }

    @Test
    void qosFilterReadsInitializationParameters() {
        QoSFilter filter = new QoSFilter();
        Map<String, String> parameters = Map.of(
            "maxRequests", "4",
            "maxPriority", "3",
            "waitMs", "75",
            "suspendMs", "250");

        try {
            filter.init(new SimpleFilterConfig("configuredQoS", parameters));

            assertThat(filter.getMaxRequests()).isEqualTo(4);
            assertThat(filter.getWaitMs()).isEqualTo(75);
            assertThat(filter.getSuspendMs()).isEqualTo(250);
        } finally {
            filter.destroy();
        }
    }

    private static Response request(
        URI uri,
        String method,
        Map<String, String> headers,
        String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestMethod(method);
        headers.forEach(connection::setRequestProperty);
        if (body != null) {
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        try {
            int status = connection.getResponseCode();
            String responseBody = readBody(connection);
            return new Response(status, responseBody, connection.getHeaderFields());
        } finally {
            connection.disconnect();
        }
    }

    private static Response rawRequest(
        URI uri,
        String method,
        Map<String, String> headers,
        String body) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);

            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String target = uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(target).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(uri.getHost()).append(':').append(uri.getPort()).append("\r\n");
            request.append("Connection: close\r\n");
            if (bodyBytes.length > 0) {
                request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            }
            headers.forEach((name, value) -> request.append(name).append(": ").append(value).append("\r\n"));
            request.append("\r\n");

            OutputStream output = socket.getOutputStream();
            output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            output.write(bodyBytes);
            output.flush();

            ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(responseBytes);
            return parseRawResponse(responseBytes.toString(StandardCharsets.ISO_8859_1));
        }
    }

    private static Response parseRawResponse(String response) {
        int headerEnd = response.indexOf("\r\n\r\n");
        String headerBlock = headerEnd < 0 ? response : response.substring(0, headerEnd);
        String responseBody = headerEnd < 0 ? "" : response.substring(headerEnd + 4);
        String[] headerLines = headerBlock.split("\r\n");
        int status = Integer.parseInt(headerLines[0].split(" ", 3)[1]);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = 1; i < headerLines.length; i++) {
            int separator = headerLines[i].indexOf(':');
            if (separator > 0) {
                String name = headerLines[i].substring(0, separator);
                String value = headerLines[i].substring(separator + 1).trim();
                headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            }
        }
        return new Response(status, responseBody, headers);
    }

    private static String readBody(HttpURLConnection connection) throws IOException {
        InputStream input = connection.getErrorStream();
        if (input == null) {
            input = connection.getInputStream();
        }
        if (input == null) {
            return "";
        }
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            stream.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private record Response(int status, String body, Map<String, List<String>> headers) {
        String header(String name) {
            List<String> values = headers(name);
            if (values.isEmpty()) {
                return null;
            }
            return String.join(",", values);
        }

        List<String> headers(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return List.of();
        }
    }

    private static final class JettyServer implements AutoCloseable {
        private final Server server;
        private final ServerConnector connector;

        private JettyServer(Server server, ServerConnector connector) {
            this.server = server;
            this.connector = connector;
        }

        static JettyServer start(Servlet servlet, FilterHolder filter) throws Exception {
            ServletHolder servletHolder = new ServletHolder(servlet);
            ServletContextHandler context = contextWithServlet(servletHolder, "/*");
            context.addFilter(filter, "/*", EnumSet.of(DispatcherType.REQUEST));
            return start(context);
        }

        static JettyServer start(ServletHolder servletHolder) throws Exception {
            return start(contextWithServlet(servletHolder, "/*"));
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + connector.getLocalPort() + path);
        }

        @Override
        public void close() throws Exception {
            server.stop();
            server.join();
        }

        private static ServletContextHandler contextWithServlet(ServletHolder servletHolder, String pathSpec) {
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.setContextPath("/");
            context.addServlet(servletHolder, pathSpec);
            return context;
        }

        private static JettyServer start(ServletContextHandler context) throws Exception {
            Server server = new Server();
            server.setStopTimeout(TIMEOUT_MILLIS);
            ServerConnector connector = new ServerConnector(server);
            connector.setHost("127.0.0.1");
            connector.setPort(0);
            connector.setIdleTimeout(TIMEOUT_MILLIS);
            server.addConnector(connector);
            server.setHandler(context);
            server.start();
            return new JettyServer(server, connector);
        }
    }

    private static final class TextServlet extends HttpServlet {
        private final String text;
        private final AtomicInteger calls;

        private TextServlet(String text, AtomicInteger calls) {
            this.text = text;
            this.calls = calls;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            calls.incrementAndGet();
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(text);
        }
    }

    private static final class MimeTypeFlaggingFilter extends IncludeExcludeBasedFilter {
        @Override
        public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest)request;
            HttpServletResponse httpResponse = (HttpServletResponse)response;
            httpResponse.setHeader("X-Mime-Eligible", Boolean.toString(shouldFilter(httpRequest, httpResponse)));
            chain.doFilter(request, response);
        }
    }

    private static final class SlowServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            try {
                Thread.sleep(1_000);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("slow response");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static final class GreetingEventSourceServlet extends EventSourceServlet {
        @Override
        protected EventSource newEventSource(HttpServletRequest request) {
            return new EventSource() {
                @Override
                public void onOpen(Emitter emitter) throws IOException {
                    emitter.event("greeting", "hello\nworld");
                    emitter.comment("completed");
                    emitter.close();
                }

                @Override
                public void onClose() {
                }
            };
        }
    }

    private static final class SimpleFilterConfig implements FilterConfig {
        private final String filterName;
        private final Map<String, String> parameters;

        private SimpleFilterConfig(String filterName, Map<String, String> parameters) {
            this.filterName = filterName;
            this.parameters = new HashMap<>(parameters);
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return parameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }
    }
}
