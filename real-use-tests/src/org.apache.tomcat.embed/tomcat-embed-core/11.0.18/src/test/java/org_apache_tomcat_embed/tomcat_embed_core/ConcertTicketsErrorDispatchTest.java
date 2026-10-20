/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises error handling and request dispatching, where a `Context` maps `ErrorPage`s by
 * status code, by exception type and as a default, `RequestDispatcher` forwards to and
 * includes from other servlets, and the host's `ErrorReportValve` renders pages nobody mapped.
 *
 * A concert box office routes every event page through a front servlet that forwards to a
 * view under `WEB-INF`, which includes a shared footer; the views read the original URI and
 * query from the forward and include attributes, and a filter mapped to every dispatcher type
 * records the hops. Unknown events, sold-out shows and broken ones land on the box office's
 * own error pages, which see the status, message, exception and original request through the
 * `jakarta.servlet.error.*` attributes under the `ERROR` dispatcher. A second, legacy context
 * maps no error pages, so the host's `ErrorReportValve` answers with its plain HTML report,
 * configured to reveal neither the exception details nor the server version.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcertTicketsErrorDispatchTest {

    private static final String HOPS_ATTRIBUTE = "boxoffice.hops";

    private static final Map<String, String> EVENTS = Map.of(
            "101", "Nocturnes at the Riverside",
            "202", "Brass Night");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Tomcat tomcat;

    private Path baseDir;

    private String rootUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("boxoffice-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        ErrorReportValve reportValve = new ErrorReportValve();
        reportValve.setShowReport(false);
        reportValve.setShowServerInfo(false);
        tomcat.getHost().getPipeline().addValve(reportValve);

        configureBoxOffice(tomcat.addContext("/tickets", baseDir.toString()));
        configureLegacyKiosk(tomcat.addContext("/kiosk", baseDir.toString()));

        tomcat.start();
        rootUrl = "http://localhost:" + tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void forwardsAnEventPageToItsViewWhichIncludesTheFooter() throws Exception {
        HttpResponse<String> response = send("/tickets/events/101?seat=B12");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-View")).hasValue("event");
        assertThat(response.headers().firstValue("X-Footer")).isEmpty();
        assertThat(response.body()).isEqualTo("""
                event=Nocturnes at the Riverside
                seat=B12 layout=full
                servletPath=/WEB-INF/views pathInfo=/event
                forwardedFrom=/tickets/events/101 servlet=/events info=/101 query=seat=B12
                footer for /tickets/fragments/footer via /tickets/WEB-INF/views/event
                footer query=compact=false
                hops=REQUEST,FORWARD,INCLUDE
                """);
    }

    @Test
    void mergesForwardQueryParametersAheadOfTheOriginalOnes() throws Exception {
        HttpResponse<String> response = send("/tickets/events/202?seat=A1&layout=full");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("event=Brass Night\n")
                .contains("seat=A1 layout=compact\n")
                .contains("query=seat=A1&layout=full\n");
    }

    @Test
    void namedDispatcherForwardKeepsTheOriginalPaths() throws Exception {
        HttpResponse<String> response = send("/tickets/events/101/receipt");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("""
                receipt for Nocturnes at the Riverside
                uri=/tickets/events/101/receipt servlet=/events forwardUri=null
                hops=REQUEST
                """);
    }

    @Test
    void sendsUnknownEventsToTheNotFoundPageUnderTheErrorDispatcher() throws Exception {
        HttpResponse<String> response = send("/tickets/events/999?seat=C3");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(response.body()).isEqualTo("""
                page=not-found dispatcher=ERROR
                status=404 message=No event 999
                uri=/tickets/events/999 query=seat=C3 method=GET servlet=events
                exception=none
                hops=REQUEST,ERROR
                """);
    }

    @Test
    void routesASoldOutShowToThePageMappedForItsParentExceptionType() throws Exception {
        HttpResponse<String> response = send("/tickets/events/303");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).isEqualTo("""
                page=unavailable dispatcher=ERROR
                status=500 message=Event 303 is sold out
                uri=/tickets/events/303 query=null method=GET servlet=events
                exception=SoldOutException: Event 303 is sold out
                hops=REQUEST,ERROR
                """);
    }

    @Test
    void sendsUnmappedFailuresToTheDefaultErrorPage() throws Exception {
        HttpResponse<String> conflict = send("/tickets/events/202/refund");
        HttpResponse<String> broken = send("/tickets/events/404-broken");

        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).startsWith("""
                page=default dispatcher=ERROR
                status=409 message=Refunds closed for 202
                """);

        assertThat(broken.statusCode()).isEqualTo(500);
        assertThat(broken.body())
                .startsWith("page=default dispatcher=ERROR\nstatus=500 ")
                .contains("exception=IllegalStateException: Seating plan missing for 404-broken\n");
    }

    @Test
    void refusesDirectRequestsIntoWebInf() throws Exception {
        HttpResponse<String> response = send("/tickets/WEB-INF/views/event");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).startsWith("page=not-found dispatcher=ERROR\nstatus=404 ");
    }

    @Test
    void reportsUnmappedLegacyErrorsThroughTheHostErrorReportValve() throws Exception {
        HttpResponse<String> maintenance = send("/kiosk/print");
        HttpResponse<String> crash = send("/kiosk/crash");

        assertThat(maintenance.statusCode()).isEqualTo(503);
        assertThat(maintenance.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
        assertThat(maintenance.body())
                .contains("<title>HTTP Status 503 \u2013 Service Unavailable</title>")
                .doesNotContain("Printer jammed")
                .doesNotContain("Apache Tomcat/");

        assertThat(crash.statusCode()).isEqualTo(500);
        assertThat(crash.body())
                .contains("<h1>HTTP Status 500 \u2013 Internal Server Error</h1>")
                .doesNotContain("Kiosk database offline")
                .doesNotContain("IllegalStateException");
    }

    private static void configureBoxOffice(Context context) {
        Tomcat.addServlet(context, "events", new EventServlet());
        context.addServletMappingDecoded("/events/*", "events");
        Tomcat.addServlet(context, "views", new EventViewServlet());
        context.addServletMappingDecoded("/WEB-INF/views/*", "views");
        Tomcat.addServlet(context, "footer", new FooterServlet());
        context.addServletMappingDecoded("/fragments/footer", "footer");
        Tomcat.addServlet(context, "receipt", new ReceiptServlet());
        Tomcat.addServlet(context, "errors", new ErrorPageServlet());
        context.addServletMappingDecoded("/errors/*", "errors");

        FilterDef hopsFilter = new FilterDef();
        hopsFilter.setFilterName("hops");
        hopsFilter.setFilter(new DispatchHopsFilter());
        context.addFilterDef(hopsFilter);
        FilterMap hopsMapping = new FilterMap();
        hopsMapping.setFilterName("hops");
        hopsMapping.addURLPattern("/*");
        hopsMapping.setDispatcher("REQUEST");
        hopsMapping.setDispatcher("FORWARD");
        hopsMapping.setDispatcher("INCLUDE");
        hopsMapping.setDispatcher("ERROR");
        context.addFilterMap(hopsMapping);

        ErrorPage notFound = new ErrorPage();
        notFound.setErrorCode(HttpServletResponse.SC_NOT_FOUND);
        notFound.setLocation("/errors/not-found");
        context.addErrorPage(notFound);

        ErrorPage unavailable = new ErrorPage();
        unavailable.setExceptionType(EventUnavailableException.class.getName());
        unavailable.setLocation("/errors/unavailable");
        context.addErrorPage(unavailable);

        ErrorPage fallback = new ErrorPage();
        fallback.setLocation("/errors/default");
        context.addErrorPage(fallback);
    }

    private static void configureLegacyKiosk(Context context) {
        Tomcat.addServlet(context, "kiosk", new KioskServlet());
        context.addServletMappingDecoded("/*", "kiosk");
    }

    private HttpResponse<String> send(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(rootUrl + path))
                .header("Accept-Language", "en")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static List<String> hops(ServletRequest request) {
        return (List<String>) request.getAttribute(HOPS_ATTRIBUTE);
    }

    private static void printHops(ServletRequest request, PrintWriter writer) {
        writer.println("hops=" + String.join(",", hops(request)));
    }

    /** Raised when an event cannot be sold; mapped to the box office's unavailable page. */
    static class EventUnavailableException extends RuntimeException {

        EventUnavailableException(String message) {
            super(message);
        }
    }

    /** A show whose seats are all taken, routed through its parent type's error page. */
    static final class SoldOutException extends EventUnavailableException {

        SoldOutException(String message) {
            super(message);
        }
    }

    /** Records every dispatcher type a request passes through on one shared list. */
    static final class DispatchHopsFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            List<String> hops = hops(request);
            if (hops == null) {
                hops = new ArrayList<>();
                request.setAttribute(HOPS_ATTRIBUTE, hops);
            }
            hops.add(request.getDispatcherType().name());
            chain.doFilter(request, response);
        }
    }

    /** Front controller that looks up an event and hands it to a view or an error page. */
    static final class EventServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            String[] segments = request.getPathInfo().substring(1).split("/");
            String id = segments[0];
            if (id.equals("303")) {
                throw new SoldOutException("Event 303 is sold out");
            }
            if (id.endsWith("-broken")) {
                throw new IllegalStateException("Seating plan missing for " + id);
            }
            String title = EVENTS.get(id);
            if (title == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No event " + id);
                return;
            }
            request.setAttribute("event", title);
            if (segments.length > 1 && segments[1].equals("refund")) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Refunds closed for " + id);
                return;
            }
            if (segments.length > 1 && segments[1].equals("receipt")) {
                getServletContext().getNamedDispatcher("receipt").forward(request, response);
                return;
            }
            String layout = id.equals("202") ? "compact" : "full";
            request.getRequestDispatcher("/WEB-INF/views/event?layout=" + layout).forward(request, response);
        }
    }

    /** Renders an event page from the forwarded request and includes the shared footer. */
    static final class EventViewServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("X-View", "event");
            PrintWriter writer = response.getWriter();
            writer.println("event=" + request.getAttribute("event"));
            writer.println("seat=" + request.getParameter("seat") + " layout=" + request.getParameter("layout"));
            writer.println("servletPath=" + request.getServletPath() + " pathInfo=" + request.getPathInfo());
            writer.println("forwardedFrom=" + request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI)
                    + " servlet=" + request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH)
                    + " info=" + request.getAttribute(RequestDispatcher.FORWARD_PATH_INFO)
                    + " query=" + request.getAttribute(RequestDispatcher.FORWARD_QUERY_STRING));
            request.getRequestDispatcher("/fragments/footer?compact=false").include(request, response);
            printHops(request, writer);
        }
    }

    /** Shared footer whose header writes are ignored because it only runs as an include. */
    static final class FooterServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setHeader("X-Footer", "ignored");
            PrintWriter writer = response.getWriter();
            writer.println("footer for " + request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI)
                    + " via " + request.getRequestURI());
            writer.println("footer query=" + request.getAttribute(RequestDispatcher.INCLUDE_QUERY_STRING));
        }
    }

    /** Unmapped servlet reached only by name, so the request keeps its original paths. */
    static final class ReceiptServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            PrintWriter writer = response.getWriter();
            writer.println("receipt for " + request.getAttribute("event"));
            writer.println("uri=" + request.getRequestURI() + " servlet=" + request.getServletPath()
                    + " forwardUri=" + request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI));
            printHops(request, writer);
        }
    }

    /** Box-office error pages that describe the failed request from the error attributes. */
    static final class ErrorPageServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            PrintWriter writer = response.getWriter();
            writer.println("page=" + request.getPathInfo().substring(1)
                    + " dispatcher=" + request.getDispatcherType());
            writer.println("status=" + request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
                    + " message=" + request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
            writer.println("uri=" + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)
                    + " query=" + request.getAttribute(RequestDispatcher.ERROR_QUERY_STRING)
                    + " method=" + request.getAttribute(RequestDispatcher.ERROR_METHOD)
                    + " servlet=" + request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME));
            writer.println("exception=" + (exception == null ? "none"
                    : exception.getClass().getSimpleName() + ": " + exception.getMessage()));
            printHops(request, writer);
        }
    }

    /** Legacy kiosk endpoints that fail without any error page of their own. */
    static final class KioskServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (request.getPathInfo().equals("/crash")) {
                throw new IllegalStateException("Kiosk database offline");
            }
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Printer jammed");
        }
    }
}
