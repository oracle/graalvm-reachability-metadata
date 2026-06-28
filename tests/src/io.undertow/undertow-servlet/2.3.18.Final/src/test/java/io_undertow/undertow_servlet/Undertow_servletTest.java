/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.undertow.Undertow;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.MimeMapping;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.handlers.DefaultServlet;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Undertow_servletTest {
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void embeddedDeploymentRunsListenersFiltersForwardingAndSessions() throws Exception {
        DeploymentInfo deploymentInfo = baseDeployment("pipeline")
                .addInitParameter("application.name", "undertow-servlet-test")
                .addServlets(
                        Servlets.servlet("front", FrontControllerServlet.class).addMapping("/front"),
                        Servlets.servlet("target", TargetServlet.class).addMapping("/target"),
                        Servlets.servlet("session", SessionServlet.class).addMapping("/session"))
                .addFilter(Servlets.filter("marker", MarkerFilter.class).addInitParam("marker", "filter-init"))
                .addFilterUrlMapping("marker", "/*", DispatcherType.REQUEST)
                .addListener(Servlets.listener(ApplicationListener.class));

        try (RunningServer server = startServer(deploymentInfo);
                HttpClient client = newHttpClient()) {
            HttpResponse<String> forwarded = get(client, server.resolve("/front?name=GraalVM"));
            assertThat(forwarded.statusCode()).isEqualTo(200);
            assertThat(forwarded.headers().firstValue("X-Marker")).contains("filter-init");
            assertThat(forwarded.body()).contains(
                    "name=GraalVM",
                    "filter=filter-init",
                    "listener=started",
                    "application=undertow-servlet-test");

            HttpResponse<String> firstSessionRequest = get(client, server.resolve("/session"));
            String sessionCookie = firstSessionRequest.headers().allValues("Set-Cookie").stream()
                    .filter(cookie -> cookie.startsWith("JSESSIONID="))
                    .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                    .findFirst()
                    .orElseThrow();
            HttpResponse<String> secondSessionRequest = getWithCookie(
                    client, server.resolve("/session"), sessionCookie);
            assertThat(firstSessionRequest.body()).contains("visits=1");
            assertThat(secondSessionRequest.body()).contains("visits=2");
        }
    }

    @Test
    void servletContainerInitializerRegistersServletAndErrorPageHandlesException() throws Exception {
        DeploymentInfo deploymentInfo = baseDeployment("initializer")
                .addServletContainerInitializer(new ServletContainerInitializerInfo(
                        DynamicServletInitializer.class, Set.of()))
                .addServlets(
                        Servlets.servlet("failing", FailingServlet.class).addMapping("/boom"),
                        Servlets.servlet("error", ErrorServlet.class).addMapping("/error"))
                .addErrorPage(Servlets.errorPage("/error", IllegalStateException.class));

        try (RunningServer server = startServer(deploymentInfo);
                HttpClient client = newHttpClient()) {
            HttpResponse<String> dynamicResponse = get(client, server.resolve("/dynamic"));
            assertThat(dynamicResponse.statusCode()).isEqualTo(200);
            assertThat(dynamicResponse.body()).contains("dynamic=true", "init=from-initializer");

            HttpResponse<String> errorResponse = get(client, server.resolve("/boom"));
            assertThat(errorResponse.statusCode()).isEqualTo(500);
            assertThat(errorResponse.body()).contains(
                    "status=500",
                    "exception=java.lang.IllegalStateException",
                    "message=intentional failure");
        }
    }

    @Test
    void multipartServletParsesFormFieldsAndUploadedFile(@TempDir Path tempDir) throws Exception {
        DeploymentInfo deploymentInfo = baseDeployment("multipart")
                .setTempDir(tempDir)
                .addServlet(Servlets.servlet("upload", UploadServlet.class)
                        .addMapping("/upload")
                        .setMultipartConfig(Servlets.multipartConfig(
                                tempDir.toString(), 1024 * 1024, 1024 * 1024, 0)));

        String boundary = "----undertowServletBoundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"description\"\r\n\r\n"
                + "native-image friendly upload\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"hello.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "hello from multipart\r\n"
                + "--" + boundary + "--\r\n";

        try (RunningServer server = startServer(deploymentInfo);
                HttpClient client = newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(server.resolve("/upload"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(
                    "parts=2",
                    "description=native-image friendly upload",
                    "filename=hello.txt",
                    "content=hello from multipart");
        }
    }

    @Test
    void asyncServletCompletesResponseOnContainerWorkerThread() throws Exception {
        DeploymentInfo deploymentInfo = baseDeployment("async")
                .addServlet(Servlets.servlet("async", AsyncServlet.class)
                        .addMapping("/async")
                        .setAsyncSupported(true));

        try (RunningServer server = startServer(deploymentInfo);
                HttpClient client = newHttpClient()) {
            HttpResponse<String> response = get(client, server.resolve("/async?message=complete"));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(
                    "asyncSupported=true",
                    "message=complete",
                    "complete=true");
        }
    }

    @Test
    void defaultServletServesWelcomeFilesAndConfiguredMimeMappings(@TempDir Path webRoot) throws Exception {
        Files.writeString(webRoot.resolve("index.txt"), "welcome from resource manager", StandardCharsets.UTF_8);
        Path assets = Files.createDirectories(webRoot.resolve("assets"));
        Files.writeString(assets.resolve("info.example"), "custom mime resource", StandardCharsets.UTF_8);

        DeploymentInfo deploymentInfo = baseDeployment("static")
                .setResourceManager(new PathResourceManager(webRoot, 1024))
                .addWelcomePage("index.txt")
                .addMimeMapping(new MimeMapping("example", "application/x-undertow-test"))
                .addServlet(Servlets.servlet("default", DefaultServlet.class)
                        .addInitParam(DefaultServlet.DEFAULT_ALLOWED, "true")
                        .addMapping("/"));

        try (RunningServer server = startServer(deploymentInfo);
                HttpClient client = newHttpClient()) {
            HttpResponse<String> welcomeResponse = get(client, server.resolve("/"));
            assertThat(welcomeResponse.statusCode()).isEqualTo(200);
            assertThat(welcomeResponse.body()).contains("welcome from resource manager");

            HttpResponse<String> resourceResponse = get(client, server.resolve("/assets/info.example"));
            assertThat(resourceResponse.statusCode()).isEqualTo(200);
            assertThat(resourceResponse.headers().firstValue("Content-Type"))
                    .contains("application/x-undertow-test");
            assertThat(resourceResponse.body()).contains("custom mime resource");
        }
    }

    private static DeploymentInfo baseDeployment(String name) {
        return Servlets.deployment()
                .setClassLoader(Undertow_servletTest.class.getClassLoader())
                .setContextPath("/" + name)
                .setDeploymentName(name + ".war")
                .setDefaultEncoding(StandardCharsets.UTF_8.name());
    }

    private static RunningServer startServer(DeploymentInfo deploymentInfo) throws ServletException {
        ServletContainer container = Servlets.newContainer();
        DeploymentManager manager = container.addDeployment(deploymentInfo);
        manager.deploy();
        Undertow server = Undertow.builder()
                .addHttpListener(0, LOOPBACK_HOST)
                .setHandler(manager.start())
                .build();
        server.start();
        return new RunningServer(container, deploymentInfo, manager, server);
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getWithCookie(HttpClient client, URI uri, String cookie)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Cookie", cookie)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class RunningServer implements AutoCloseable {
        private final ServletContainer container;
        private final DeploymentInfo deploymentInfo;
        private final DeploymentManager manager;
        private final Undertow server;
        private final URI baseUri;

        private RunningServer(
                ServletContainer container,
                DeploymentInfo deploymentInfo,
                DeploymentManager manager,
                Undertow server) {
            this.container = container;
            this.deploymentInfo = deploymentInfo;
            this.manager = manager;
            this.server = server;
            InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
            this.baseUri = URI.create("http://" + LOOPBACK_HOST + ":" + address.getPort());
        }

        private URI resolve(String pathAndQuery) {
            return baseUri.resolve(baseUri.getPath() + pathAndQuery);
        }

        @Override
        public void close() throws ServletException {
            server.stop();
            if (manager.getState() == DeploymentManager.State.STARTED) {
                manager.stop();
            }
            if (manager.getState() == DeploymentManager.State.DEPLOYED) {
                manager.undeploy();
            }
            container.removeDeployment(deploymentInfo);
        }
    }

    public static final class ApplicationListener implements ServletContextListener {
        @Override
        public void contextInitialized(ServletContextEvent event) {
            event.getServletContext().setAttribute("listener.state", "started");
        }
    }

    public static final class MarkerFilter implements Filter {
        private String marker;

        @Override
        public void init(FilterConfig filterConfig) {
            marker = filterConfig.getInitParameter("marker");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            request.setAttribute("filter.marker", marker);
            ((HttpServletResponse) response).setHeader("X-Marker", marker);
            chain.doFilter(request, response);
        }
    }

    public static final class FrontControllerServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            request.setAttribute("forwarded.name", request.getParameter("name"));
            request.getRequestDispatcher("/target").forward(request, response);
        }
    }

    public static final class TargetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain;charset=UTF-8");
            ServletContext context = request.getServletContext();
            response.getWriter().println("name=" + request.getAttribute("forwarded.name"));
            response.getWriter().println("filter=" + request.getAttribute("filter.marker"));
            response.getWriter().println("listener=" + context.getAttribute("listener.state"));
            response.getWriter().println("application=" + context.getInitParameter("application.name"));
        }
    }

    public static final class SessionServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession(true);
            Integer visits = (Integer) session.getAttribute("visits");
            int nextVisit = visits == null ? 1 : visits + 1;
            session.setAttribute("visits", nextVisit);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().println("visits=" + nextVisit);
        }
    }

    public static final class DynamicServletInitializer implements ServletContainerInitializer {
        @Override
        public void onStartup(Set<Class<?>> classes, ServletContext context) {
            ServletRegistration.Dynamic registration = context.addServlet("dynamic", DynamicServlet.class);
            registration.setInitParameter("createdBy", "from-initializer");
            registration.addMapping("/dynamic");
        }
    }

    public static final class DynamicServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().println("dynamic=true");
            response.getWriter().println("init=" + getServletConfig().getInitParameter("createdBy"));
        }
    }

    public static final class FailingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            throw new IllegalStateException("intentional failure");
        }
    }

    public static final class ErrorServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().println("status=" + request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
            response.getWriter().println("exception=" + exception.getClass().getName());
            response.getWriter().println("message=" + exception.getMessage());
        }
    }

    public static final class UploadServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            Collection<Part> parts = request.getParts();
            Part file = request.getPart("file");
            String content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().println("parts=" + parts.size());
            response.getWriter().println("description=" + request.getParameter("description"));
            response.getWriter().println("filename=" + file.getSubmittedFileName());
            response.getWriter().println("content=" + content);
        }
    }

    public static final class AsyncServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setContentType("text/plain;charset=UTF-8");
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(HTTP_TIMEOUT.toMillis());
            asyncContext.start(new AsyncResponseTask(
                    asyncContext, request.isAsyncSupported(), request.getParameter("message")));
        }
    }

    public static final class AsyncResponseTask implements Runnable {
        private final AsyncContext asyncContext;
        private final boolean asyncSupported;
        private final String message;

        private AsyncResponseTask(AsyncContext asyncContext, boolean asyncSupported, String message) {
            this.asyncContext = asyncContext;
            this.asyncSupported = asyncSupported;
            this.message = message;
        }

        @Override
        public void run() {
            try {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                response.getWriter().println("asyncSupported=" + asyncSupported);
                response.getWriter().println("message=" + message);
                response.getWriter().println("complete=true");
                asyncContext.complete();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to write asynchronous response", e);
            }
        }
    }
}
