/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.servlet.CookieWrapper;
import org.glassfish.grizzly.servlet.ServletHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Grizzly_http_servletTest {
    private static final String HOST = "127.0.0.1";
    private static final int TIMEOUT_MILLIS = 3_000;

    @Test
    void servletHandlerServesRequestsThroughFiltersSessionsAndCookies() throws Exception {
        EchoServlet servlet = new EchoServlet();
        CapturingFilter filter = new CapturingFilter();
        ServletHandler handler = new ServletHandler(servlet);
        handler.setContextPath("/sample");
        handler.setServletPath("/echo");
        handler.addInitParameter("greeting", "hello");
        handler.addContextParameter("application", "grizzly-servlet-test");
        handler.addFilter(filter, "capturingFilter", Collections.singletonMap("role", "observed"));

        ServerHandle server = startServer(handler, "/sample/echo/*");
        try {
            HttpResponse response = get(server.port(),
                    "/sample/echo/details?name=GraalVM&multi=one&multi=two",
                    Map.of("X-Test-Header", "present", "Cookie", "client=browser"));

            assertThat(response.status).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.header("X-Filter-Role")).isEqualTo("observed");
            assertThat(response.headersWithName("Set-Cookie"))
                    .anySatisfy(value -> assertThat(value).contains("server=grizzly"));

            Map<String, String> lines = parseBody(response.body);
            assertThat(lines).containsEntry("initGreeting", "hello");
            assertThat(lines).containsEntry("contextApplication", "grizzly-servlet-test");
            assertThat(lines).containsEntry("contextPath", "/sample");
            assertThat(lines).containsEntry("servletPath", "/echo");
            assertThat(lines).containsEntry("pathInfo", "/details");
            assertThat(lines).containsEntry("queryName", "GraalVM");
            assertThat(lines).containsEntry("multiValues", "one,two");
            assertThat(lines).containsEntry("requestHeader", "present");
            assertThat(lines).containsEntry("clientCookie", "browser");
            assertThat(lines).containsEntry("filterAttribute", "observed");
            assertThat(lines).containsEntry("sessionAttribute", "created");
            assertThat(lines).containsEntry("method", "GET");
            assertThat(lines).containsEntry("localeLanguage", Locale.US.getLanguage());
            assertThat(Integer.parseInt(lines.get("sessionIdLength"))).isPositive();

            assertThat(servlet.initCount).hasValue(1);
            assertThat(servlet.requestCount).hasValue(1);
            assertThat(filter.initCount).hasValue(1);
            assertThat(filter.requestCount).hasValue(1);
        } finally {
            server.close();
        }

        assertThat(servlet.destroyCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(filter.destroyCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void requestDispatcherIncludesAndForwardsWithinServletContext() throws Exception {
        DispatchingServlet servlet = new DispatchingServlet();
        ServletHandler handler = new ServletHandler(servlet);
        handler.setContextPath("");
        handler.setServletPath("/dispatch");

        ServerHandle server = startServer(handler, "/dispatch/*");
        try {
            HttpResponse includeResponse = get(server.port(), "/dispatch/include", Collections.emptyMap());

            assertThat(includeResponse.status).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(includeResponse.body).contains("before-include");
            assertThat(includeResponse.body).contains("targetPath=/target");
            assertThat(includeResponse.body).contains("includedAttribute=from-include");
            assertThat(includeResponse.body).contains("after-include");

            HttpResponse forwardResponse = get(server.port(), "/dispatch/forward", Collections.emptyMap());

            assertThat(forwardResponse.status).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(forwardResponse.body).doesNotContain("before-forward");
            assertThat(forwardResponse.body).contains("targetPath=/target");
            assertThat(forwardResponse.body).contains("includedAttribute=from-forward");
        } finally {
            server.close();
        }
    }

    @Test
    void postRequestBodyAndFormParametersAreAvailableToServlet() throws Exception {
        ServletHandler handler = new ServletHandler(new FormServlet());
        handler.setContextPath("/forms");
        handler.setServletPath("/submit");

        ServerHandle server = startServer(handler, "/forms/submit");
        try {
            HttpResponse response = post(server.port(), "/forms/submit", "alpha=one&beta=two&beta=three");

            assertThat(response.status).isEqualTo(HttpURLConnection.HTTP_OK);
            Map<String, String> lines = parseBody(response.body);
            assertThat(lines).containsEntry("contentType", "application/x-www-form-urlencoded");
            assertThat(lines).containsEntry("alpha", "one");
            assertThat(lines).containsEntry("beta", "two,three");
            assertThat(lines).containsEntry("contentLength", "29");
        } finally {
            server.close();
        }
    }

    @Test
    void cookieWrapperDelegatesToWrappedServletCookie() {
        Cookie servletCookie = new Cookie("theme", "light");
        CookieWrapper wrapper = new CookieWrapper("ignored", "ignored");
        wrapper.setWrappedCookie(servletCookie);

        wrapper.setValue("dark");
        wrapper.setComment("ui preference");
        wrapper.setDomain("example.test");
        wrapper.setPath("/ui");
        wrapper.setMaxAge(120);
        wrapper.setSecure(true);
        wrapper.setVersion(1);

        assertThat(wrapper.getName()).isEqualTo("theme");
        assertThat(wrapper.getValue()).isEqualTo("dark");
        assertThat(wrapper.getComment()).isEqualTo("ui preference");
        assertThat(wrapper.getDomain()).isEqualTo("example.test");
        assertThat(wrapper.getPath()).isEqualTo("/ui");
        assertThat(wrapper.getMaxAge()).isEqualTo(120);
        assertThat(wrapper.isSecure()).isTrue();
        assertThat(wrapper.getVersion()).isEqualTo(1);
        assertThat(wrapper.getWrappedCookie()).isSameAs(servletCookie);

        Cookie clone = (Cookie) wrapper.clone();
        assertThat(clone).isNotSameAs(servletCookie);
        assertThat(clone.getName()).isEqualTo("theme");
        assertThat(clone.getValue()).isEqualTo("dark");
    }

    private static ServerHandle startServer(ServletHandler handler, String mapping) throws IOException {
        int port = availablePort();
        HttpServer server = new HttpServer();
        server.addListener(new NetworkListener("test-listener", HOST, port));
        server.getServerConfiguration().addHttpHandler(handler, mapping);
        server.start();
        return new ServerHandle(server, port);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpResponse get(int port, String path, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = openConnection(port, path);
        headers.forEach(connection::setRequestProperty);
        return readResponse(connection);
    }

    private static HttpResponse post(int port, String path, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = openConnection(port, path);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
        return readResponse(connection);
    }

    private static HttpURLConnection openConnection(int port, String path) throws IOException {
        URL url = new URL("http", HOST, port, path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Connection", "close");
        return connection;
    }

    private static HttpResponse readResponse(HttpURLConnection connection) throws IOException {
        try {
            int status = connection.getResponseCode();
            InputStream input = status >= HttpURLConnection.HTTP_BAD_REQUEST
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String body = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new HttpResponse(status, body, connection.getHeaderFields());
        } finally {
            connection.disconnect();
        }
    }

    private static Map<String, String> parseBody(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : body.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return values;
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return "";
    }

    private static String join(String[] values) {
        return values == null ? "" : String.join(",", values);
    }

    private record ServerHandle(HttpServer server, int port) implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
        }
    }

    private record HttpResponse(int status, String body, Map<String, List<String>> headers) {
        private String header(String name) {
            List<String> values = headersWithName(name);
            return values.isEmpty() ? null : values.get(0);
        }

        private List<String> headersWithName(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .toList();
        }
    }

    private static final class CapturingFilter implements Filter {
        private final AtomicInteger initCount = new AtomicInteger();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger destroyCount = new AtomicInteger();
        private String role;

        @Override
        public void init(FilterConfig filterConfig) {
            role = filterConfig.getInitParameter("role");
            initCount.incrementAndGet();
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            requestCount.incrementAndGet();
            request.setAttribute("filter.role", role);
            ((HttpServletResponse) response).setHeader("X-Filter-Role", role);
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            destroyCount.incrementAndGet();
        }
    }

    private static final class EchoServlet extends HttpServlet {
        private final AtomicInteger initCount = new AtomicInteger();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger destroyCount = new AtomicInteger();

        @Override
        public void init() {
            initCount.incrementAndGet();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            requestCount.incrementAndGet();
            HttpSession session = request.getSession(true);
            session.setAttribute("state", "created");
            response.addCookie(new Cookie("server", "grizzly"));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain");
            response.setLocale(Locale.US);
            response.getWriter().println("initGreeting=" + getInitParameter("greeting"));
            response.getWriter().println("contextApplication=" + getServletContext().getInitParameter("application"));
            response.getWriter().println("contextPath=" + request.getContextPath());
            response.getWriter().println("servletPath=" + request.getServletPath());
            response.getWriter().println("pathInfo=" + request.getPathInfo());
            response.getWriter().println("queryName=" + request.getParameter("name"));
            response.getWriter().println("multiValues=" + join(request.getParameterValues("multi")));
            response.getWriter().println("requestHeader=" + request.getHeader("X-Test-Header"));
            response.getWriter().println("clientCookie=" + cookieValue(request, "client"));
            response.getWriter().println("filterAttribute=" + request.getAttribute("filter.role"));
            response.getWriter().println("sessionAttribute=" + session.getAttribute("state"));
            response.getWriter().println("sessionIdLength=" + session.getId().length());
            response.getWriter().println("method=" + request.getMethod());
            response.getWriter().println("localeLanguage=" + response.getLocale().getLanguage());
        }

        @Override
        public void destroy() {
            destroyCount.incrementAndGet();
        }
    }

    private static final class DispatchingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain");
            if (request.getAttribute("dispatch.attribute") != null || "/target".equals(request.getPathInfo())) {
                response.getWriter().println("targetPath=/target");
                response.getWriter().println("includedAttribute=" + request.getAttribute("dispatch.attribute"));
                return;
            }
            if ("/include".equals(request.getPathInfo())) {
                request.setAttribute("dispatch.attribute", "from-include");
                response.getWriter().println("before-include");
                request.getRequestDispatcher("/dispatch/target").include(request, response);
                response.getWriter().println("after-include");
            } else if ("/forward".equals(request.getPathInfo())) {
                request.setAttribute("dispatch.attribute", "from-forward");
                response.getWriter().println("before-forward");
                RequestDispatcher dispatcher = request.getRequestDispatcher("/dispatch/target");
                dispatcher.forward(request, response);
            }
        }
    }

    private static final class FormServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain");
            response.getWriter().println("contentType=" + request.getContentType());
            response.getWriter().println("alpha=" + request.getParameter("alpha"));
            response.getWriter().println("beta=" + join(request.getParameterValues("beta")));
            response.getWriter().println("contentLength=" + request.getContentLength());
        }
    }
}
