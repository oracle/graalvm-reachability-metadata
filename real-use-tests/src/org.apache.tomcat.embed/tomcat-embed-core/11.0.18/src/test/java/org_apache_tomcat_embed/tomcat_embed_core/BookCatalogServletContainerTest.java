/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serves a book catalog from the embedded servlet container, where `Tomcat` hosts a
 * programmatic `Context` whose `HttpServlet` and API-key `Filter` are registered through
 * `Tomcat.addServlet`, `FilterDef` and `FilterMap` and reached over the HTTP/1.1 connector.
 *
 * A bookshop embeds its catalog service in a plain Java process instead of deploying a WAR.
 * Clients list and look up books, add new ones with a form `POST` that answers `201` and a
 * `Location` header, and get `404` for unknown ISBNs. The filter rejects calls without the
 * shop's API key before the servlet runs and hands the caller's identity to the servlet as
 * a request attribute; the container decodes UTF-8 form bodies and reports the context path
 * and path info the mapping produced. Stopping the server leaves it in the `DESTROYED` state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookCatalogServletContainerTest {

    private static final String API_KEY = "shop-secret-42";

    private static final String CALLER_ATTRIBUTE = "bookshop.caller";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ApiKeyFilter apiKeyFilter = new ApiKeyFilter();

    private Tomcat tomcat;

    private Path baseDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("bookshop-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("/shop", baseDir.toString());
        Tomcat.addServlet(context, "catalog", new CatalogServlet());
        context.addServletMappingDecoded("/books/*", "catalog");

        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName("apiKey");
        filterDef.setFilter(apiKeyFilter);
        context.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("apiKey");
        filterMap.addURLPattern("/*");
        context.addFilterMap(filterMap);

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/shop";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        assertThat(tomcat.getServer().getState()).isEqualTo(LifecycleState.DESTROYED);
        try (var paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void listsSeededBooksForAnAuthorizedCaller() throws Exception {
        HttpResponse<String> response = send(get("/books").header("X-Api-Key", API_KEY));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(response.headers().firstValue("X-Served-For")).hasValue("clerk");
        assertThat(response.body()).contains(
                "978-0134685991 | Effective Java",
                "978-1617294945 | Spring in Action");
    }

    @Test
    void looksUpOneBookByPathInfo() throws Exception {
        HttpResponse<String> response = send(get("/books/978-0134685991")
                .header("X-Api-Key", API_KEY));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("context=/shop servlet=/books isbn=978-0134685991 title=Effective Java\n");
    }

    @Test
    void answersNotFoundForAnUnknownIsbn() throws Exception {
        HttpResponse<String> response = send(get("/books/000-0000000000")
                .header("X-Api-Key", API_KEY));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void addsABookFromAUtf8FormPost() throws Exception {
        String form = "isbn=978-3-16-148410-0&title=Der+Pr%C3%BCfer+%E2%80%93+Band+1";
        HttpResponse<String> created = send(HttpRequest.newBuilder(URI.create(baseUrl + "/books"))
                .header("X-Api-Key", API_KEY)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)));

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location"))
                .hasValue("/shop/books/978-3-16-148410-0");

        HttpResponse<String> fetched = send(get("/books/978-3-16-148410-0")
                .header("X-Api-Key", API_KEY));
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).endsWith("title=Der Pr\u00fcfer \u2013 Band 1\n");
    }

    @Test
    void rejectsCallsWithoutTheApiKeyBeforeTheServletRuns() throws Exception {
        int servedBefore = CatalogServlet.SERVED.get();

        HttpResponse<String> missing = send(get("/books"));
        HttpResponse<String> wrong = send(get("/books").header("X-Api-Key", "guess"));

        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.headers().firstValue("WWW-Authenticate")).hasValue("ApiKey realm=\"bookshop\"");
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(CatalogServlet.SERVED.get()).isEqualTo(servedBefore);
        assertThat(apiKeyFilter.rejected.get()).isGreaterThanOrEqualTo(2);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Admits requests that carry the shop's API key and names the caller for the servlet. */
    static final class ApiKeyFilter implements Filter {

        private final AtomicInteger rejected = new AtomicInteger();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (!API_KEY.equals(httpRequest.getHeader("X-Api-Key"))) {
                rejected.incrementAndGet();
                httpResponse.setHeader("WWW-Authenticate", "ApiKey realm=\"bookshop\"");
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            httpRequest.setAttribute(CALLER_ATTRIBUTE, "clerk");
            chain.doFilter(request, response);
        }
    }

    /** Lists, looks up and adds books held in memory, keyed by ISBN. */
    static final class CatalogServlet extends HttpServlet {

        static final AtomicInteger SERVED = new AtomicInteger();

        private final Map<String, String> books = new ConcurrentHashMap<>(Map.of(
                "978-0134685991", "Effective Java",
                "978-1617294945", "Spring in Action"));

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            SERVED.incrementAndGet();
            prepare(request, response);
            String pathInfo = request.getPathInfo();
            PrintWriter writer = response.getWriter();
            if (pathInfo == null || pathInfo.equals("/")) {
                new TreeMap<>(books).forEach((isbn, title) -> writer.println(isbn + " | " + title));
                return;
            }
            String isbn = pathInfo.substring(1);
            String title = books.get(isbn);
            if (title == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            writer.println("context=" + request.getContextPath() + " servlet=" + request.getServletPath()
                    + " isbn=" + isbn + " title=" + title);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            SERVED.incrementAndGet();
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            prepare(request, response);
            String isbn = request.getParameter("isbn");
            String title = request.getParameter("title");
            if (isbn == null || title == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            books.put(isbn, title);
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setHeader("Location", request.getContextPath() + request.getServletPath() + "/" + isbn);
        }

        private static void prepare(HttpServletRequest request, HttpServletResponse response) {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("X-Served-For", (String) request.getAttribute(CALLER_ATTRIBUTE));
        }
    }
}
