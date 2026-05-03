/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_servlet;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class Undertow_servletTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final AtomicInteger DEPLOYMENT_IDS = new AtomicInteger();

    @Test
    void deploymentRunsListenersFiltersServletInitParametersAndSessions() throws Exception {
        ApplicationListener.INITIALIZED.set(0);
        ApplicationListener.DESTROYED.set(0);

        DeploymentInfo deploymentInfo = baseDeployment("/app")
                .addInitParameter("applicationName", "undertow-servlet")
                .addListener(Servlets.listener(ApplicationListener.class,
                        new ImmediateInstanceFactory<>(new ApplicationListener())))
                .addFilter(Servlets.filter("headerFilter", HeaderFilter.class,
                                new ImmediateInstanceFactory<>(new HeaderFilter()))
                        .addInitParam("filterValue", "filtered"))
                .addFilterUrlMapping("headerFilter", "/*", DispatcherType.REQUEST)
                .addServlet(Servlets.servlet("stateful", StatefulServlet.class,
                                new ImmediateInstanceFactory<>(new StatefulServlet()))
                        .addInitParam("servletValue", "configured")
                        .addMapping("/state"));

        try (ManagedServer server = start(deploymentInfo); HttpClient client = newHttpClient()) {
            HttpResponse<String> firstResponse = client.send(get(server.uri("/state?name=GraalVM")).build(),
                    HttpResponse.BodyHandlers.ofString());
            String sessionCookie = firstResponse.headers().firstValue("Set-Cookie")
                    .map(cookie -> cookie.split(";", 2)[0])
                    .orElseThrow(() -> new AssertionError("Expected servlet container to create a session cookie"));

            assertThat(firstResponse.statusCode()).isEqualTo(200);
            assertThat(firstResponse.headers().firstValue("X-Undertow-Filter")).contains("filtered");
            assertThat(firstResponse.body()).contains(
                    "servlet=configured",
                    "context=undertow-servlet",
                    "listener=ready",
                    "filter=filtered",
                    "name=GraalVM",
                    "sessionCount=1");

            HttpResponse<String> secondResponse = client.send(get(server.uri("/state?name=Native"))
                            .header("Cookie", sessionCookie)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(secondResponse.statusCode()).isEqualTo(200);
            assertThat(secondResponse.body()).contains("name=Native", "sessionCount=2");
        }

        assertThat(ApplicationListener.INITIALIZED.get()).isEqualTo(1);
        assertThat(ApplicationListener.DESTROYED.get()).isEqualTo(1);
    }

    @Test
    void requestDispatcherForwardsAndIncludesServletResponses() throws Exception {
        DeploymentInfo deploymentInfo = baseDeployment("/dispatch")
                .addServlets(
                        Servlets.servlet("front", FrontControllerServlet.class,
                                new ImmediateInstanceFactory<>(new FrontControllerServlet())).addMapping("/front"),
                        Servlets.servlet("target", TargetServlet.class,
                                new ImmediateInstanceFactory<>(new TargetServlet())).addMapping("/target"));

        try (ManagedServer server = start(deploymentInfo); HttpClient client = newHttpClient()) {
            HttpResponse<String> forwardResponse = client.send(get(server.uri("/front?action=forward")).build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> includeResponse = client.send(get(server.uri("/front?action=include")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(forwardResponse.statusCode()).isEqualTo(200);
            assertThat(forwardResponse.body()).isEqualTo("target:forwarded");
            assertThat(includeResponse.statusCode()).isEqualTo(200);
            assertThat(includeResponse.body()).isEqualTo("before|target:included|after");
        }
    }

    @Test
    void asyncServletCompletesResponseOnContainerThread() throws Exception {
        AsyncEchoServlet.COMPLETIONS.set(0);
        AsyncEchoServlet.COMPLETION_LATCH = new CountDownLatch(1);

        DeploymentInfo deploymentInfo = baseDeployment("/async")
                .addServlet(Servlets.servlet("asyncEcho", AsyncEchoServlet.class,
                                new ImmediateInstanceFactory<>(new AsyncEchoServlet()))
                        .setAsyncSupported(true)
                        .addMapping("/echo"));

        try (ManagedServer server = start(deploymentInfo); HttpClient client = newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(server.uri("/echo"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString("hello async", StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Async")).contains("true");
            assertThat(response.body()).isEqualTo("async:hello async");
            assertThat(AsyncEchoServlet.COMPLETION_LATCH.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(AsyncEchoServlet.COMPLETIONS.get()).isEqualTo(1);
        }
    }

    @Test
    void multipartConfigParsesFormFieldsAndUploadedFiles() throws Exception {
        Path uploadDirectory = Files.createTempDirectory("undertow-servlet-upload");
        try {
            DeploymentInfo deploymentInfo = baseDeployment("/multipart")
                    .addServlet(Servlets.servlet("upload", UploadServlet.class,
                                    new ImmediateInstanceFactory<>(new UploadServlet()))
                            .setMultipartConfig(Servlets.multipartConfig(uploadDirectory.toString(), 1024, 4096, 0))
                            .addMapping("/upload"));

            try (ManagedServer server = start(deploymentInfo); HttpClient client = newHttpClient()) {
                String boundary = "UndertowServletBoundary";
                String body = String.join("\r\n",
                        "--" + boundary,
                        "Content-Disposition: form-data; name=\"field\"",
                        "",
                        "form-value",
                        "--" + boundary,
                        "Content-Disposition: form-data; name=\"file\"; filename=\"message.txt\"",
                        "Content-Type: text/plain",
                        "",
                        "uploaded text",
                        "--" + boundary + "--",
                        "");
                HttpRequest request = HttpRequest.newBuilder(server.uri("/upload"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .isEqualTo("field=form-value;file=message.txt:uploaded text:text/plain;parts=2");
            }
        } finally {
            deleteRecursively(uploadDirectory);
        }
    }

    @Test
    void defaultServletServesStaticResourcesAndWelcomePages() throws Exception {
        Path documentRoot = Files.createTempDirectory("undertow-servlet-static");
        try {
            Files.createDirectories(documentRoot.resolve("assets"));
            Files.writeString(documentRoot.resolve("index.html"), "welcome from index", StandardCharsets.UTF_8);
            Files.writeString(documentRoot.resolve("assets/message.txt"), "served from resource manager",
                    StandardCharsets.UTF_8);

            DeploymentInfo deploymentInfo = baseDeployment("/static")
                    .setResourceManager(new PathResourceManager(documentRoot))
                    .addWelcomePage("index.html")
                    .addServlet(Servlets.servlet("default", DefaultServlet.class,
                                    new ImmediateInstanceFactory<>(new DefaultServlet()))
                            .addMapping("/"));

            try (ManagedServer server = start(deploymentInfo); HttpClient client = newHttpClient()) {
                HttpResponse<String> welcomeResponse = client.send(get(server.uri("/")).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                HttpResponse<String> assetResponse = client.send(get(server.uri("/assets/message.txt")).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                assertThat(welcomeResponse.statusCode()).isEqualTo(200);
                assertThat(welcomeResponse.body()).isEqualTo("welcome from index");
                assertThat(assetResponse.statusCode()).isEqualTo(200);
                assertThat(assetResponse.body()).isEqualTo("served from resource manager");
            }
        } finally {
            deleteRecursively(documentRoot);
        }
    }

    @Test
    void configuredErrorPageReceivesServletErrorAttributes() throws Exception {
        DeploymentInfo deploymentInfo = baseDeployment("/errors")
                .addErrorPage(Servlets.errorPage("/error", ServletException.class))
                .addServlets(
                        Servlets.servlet("boom", FailingServlet.class,
                                new ImmediateInstanceFactory<>(new FailingServlet())).addMapping("/boom"),
                        Servlets.servlet("error", ErrorServlet.class,
                                new ImmediateInstanceFactory<>(new ErrorServlet())).addMapping("/error"));

        try (ManagedServer server = start(deploymentInfo); HttpClient client = newHttpClient()) {
            HttpResponse<String> response = client.send(get(server.uri("/boom")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).contains("status=500", "message=", "uri=/errors/boom");
        }
    }

    private static DeploymentInfo baseDeployment(String contextPath) {
        return Servlets.deployment()
                .setClassLoader(Undertow_servletTest.class.getClassLoader())
                .setContextPath(contextPath)
                .setDeploymentName("undertow-servlet-test-" + DEPLOYMENT_IDS.incrementAndGet());
    }

    private static ManagedServer start(DeploymentInfo deploymentInfo) throws ServletException {
        ServletContainer container = Servlets.newContainer();
        DeploymentManager manager = container.addDeployment(deploymentInfo);
        manager.deploy();
        PathHandler path = Handlers.path().addPrefixPath(deploymentInfo.getContextPath(), manager.start());
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, "localhost")
                .setHandler(path)
                .build();
        undertow.start();
        InetSocketAddress address = (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
        URI baseUri = URI.create("http://localhost:" + address.getPort() + deploymentInfo.getContextPath());
        return new ManagedServer(undertow, manager, baseUri);
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    private static HttpRequest.Builder get(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path file : stream.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static final class ManagedServer implements AutoCloseable {
        private final Undertow undertow;
        private final DeploymentManager manager;
        private final URI baseUri;

        private ManagedServer(Undertow undertow, DeploymentManager manager, URI baseUri) {
            this.undertow = undertow;
            this.manager = manager;
            this.baseUri = baseUri;
        }

        private URI uri(String path) {
            return URI.create(baseUri + path);
        }

        @Override
        public void close() throws ServletException {
            undertow.stop();
            try {
                manager.stop();
            } finally {
                manager.undeploy();
            }
        }
    }

    public static class ApplicationListener implements ServletContextListener {
        private static final AtomicInteger INITIALIZED = new AtomicInteger();
        private static final AtomicInteger DESTROYED = new AtomicInteger();

        @Override
        public void contextInitialized(ServletContextEvent event) {
            INITIALIZED.incrementAndGet();
            event.getServletContext().setAttribute("listenerState", "ready");
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
            DESTROYED.incrementAndGet();
        }
    }

    public static class HeaderFilter implements Filter {
        private String filterValue;

        @Override
        public void init(FilterConfig filterConfig) {
            filterValue = filterConfig.getInitParameter("filterValue");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            request.setAttribute("filterValue", filterValue);
            ((HttpServletResponse) response).setHeader("X-Undertow-Filter", filterValue);
            chain.doFilter(request, response);
        }
    }

    public static class StatefulServlet extends HttpServlet {
        private String servletValue;

        @Override
        public void init() {
            servletValue = getServletConfig().getInitParameter("servletValue");
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession(true);
            Integer count = (Integer) session.getAttribute("count");
            int nextCount = count == null ? 1 : count + 1;
            session.setAttribute("count", nextCount);

            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter writer = response.getWriter();
            writer.printf("servlet=%s%n", servletValue);
            writer.printf("context=%s%n", getServletContext().getInitParameter("applicationName"));
            writer.printf("listener=%s%n", getServletContext().getAttribute("listenerState"));
            writer.printf("filter=%s%n", request.getAttribute("filterValue"));
            writer.printf("name=%s%n", request.getParameter("name"));
            writer.printf("sessionCount=%d%n", nextCount);
        }
    }

    public static class FrontControllerServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            response.setContentType("text/plain; charset=UTF-8");
            if ("include".equals(request.getParameter("action"))) {
                request.setAttribute("dispatchValue", "included");
                response.getWriter().write("before|");
                request.getRequestDispatcher("/target").include(request, response);
                response.getWriter().write("|after");
                return;
            }
            request.setAttribute("dispatchValue", "forwarded");
            request.getRequestDispatcher("/target").forward(request, response);
        }
    }

    public static class TargetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("target:" + request.getAttribute("dispatchValue"));
        }
    }

    public static class AsyncEchoServlet extends HttpServlet {
        private static final AtomicInteger COMPLETIONS = new AtomicInteger();
        private static volatile CountDownLatch COMPLETION_LATCH = new CountDownLatch(1);

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String requestBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(2_000);
            asyncContext.addListener(new CompletionListener());
            asyncContext.start(() -> {
                try {
                    HttpServletResponse asyncResponse = (HttpServletResponse) asyncContext.getResponse();
                    asyncResponse.setHeader("X-Async", "true");
                    asyncResponse.setContentType("text/plain; charset=UTF-8");
                    asyncResponse.getWriter().write("async:" + requestBody);
                    asyncContext.complete();
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to write async response", ex);
                }
            });
        }
    }

    public static class CompletionListener implements AsyncListener {
        @Override
        public void onComplete(AsyncEvent event) {
            AsyncEchoServlet.COMPLETIONS.incrementAndGet();
            AsyncEchoServlet.COMPLETION_LATCH.countDown();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
        }

        @Override
        public void onError(AsyncEvent event) {
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
        }
    }

    public static class UploadServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            Collection<Part> parts = request.getParts();
            Part file = request.getPart("file");
            String fieldValue = request.getParameter("field");
            String fileContent = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().printf("field=%s;file=%s:%s:%s;parts=%d",
                    fieldValue,
                    file.getSubmittedFileName(),
                    fileContent,
                    file.getContentType(),
                    parts.size());
        }
    }

    public static class FailingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            throw new ServletException("boom");
        }
    }

    public static class ErrorServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().printf("status=%s;message=%s;uri=%s",
                    request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE),
                    request.getAttribute(RequestDispatcher.ERROR_MESSAGE),
                    request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        }
    }
}
