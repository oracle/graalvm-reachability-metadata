/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deploys a web application from its descriptors through `Tomcat.addWebapp`, letting `ContextConfig`
 * build the context from `WEB-INF/web.xml`, a `web-fragment.xml` and a `ServletContainerInitializer`
 * inside a `WEB-INF/lib` JAR, and the `META-INF/context.xml` the application ships.
 *
 * A town's lending library runs its catalog as a classic WAR-style directory. Everything is declared
 * rather than coded: the descriptor names the branch, a startup listener, the catalog servlet with its
 * page size and load-on-startup order, Tomcat's own security-header filter, request and response
 * encodings, the session cookie, MIME types, the welcome page and the not-found page. An audit JAR
 * dropped into `WEB-INF/lib` contributes a request-counting filter through its fragment and registers
 * the holds desk servlet from its initializer, while `context.xml` adds the late-fee parameter, a
 * strict same-site cookie policy and a jar scanner that stays out of the container class path.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LendingLibraryDescriptorDeploymentTest {

    private static final List<String> SHELF = List.of(
            "Moby-Dick", "Middlemarch", "Beloved", "Dune", "Emma");

    private static final String WEB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                       https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
                     version="6.1">
              <display-name>Lending Library</display-name>
              <request-character-encoding>UTF-8</request-character-encoding>
              <response-character-encoding>UTF-8</response-character-encoding>
              <context-param>
                <param-name>library.branch</param-name>
                <param-value>Riverside</param-value>
              </context-param>
              <listener>
                <listener-class>%1$s$OpeningHoursListener</listener-class>
              </listener>
              <filter>
                <filter-name>headerSecurity</filter-name>
                <filter-class>org.apache.catalina.filters.HttpHeaderSecurityFilter</filter-class>
                <init-param>
                  <param-name>antiClickJackingOption</param-name>
                  <param-value>DENY</param-value>
                </init-param>
                <init-param>
                  <param-name>hstsEnabled</param-name>
                  <param-value>false</param-value>
                </init-param>
              </filter>
              <filter-mapping>
                <filter-name>headerSecurity</filter-name>
                <url-pattern>/*</url-pattern>
              </filter-mapping>
              <servlet>
                <servlet-name>catalog</servlet-name>
                <servlet-class>%1$s$CatalogServlet</servlet-class>
                <init-param>
                  <param-name>page-size</param-name>
                  <param-value>2</param-value>
                </init-param>
                <load-on-startup>1</load-on-startup>
              </servlet>
              <servlet>
                <servlet-name>default</servlet-name>
                <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
                <init-param>
                  <param-name>listings</param-name>
                  <param-value>false</param-value>
                </init-param>
                <load-on-startup>2</load-on-startup>
              </servlet>
              <servlet-mapping>
                <servlet-name>catalog</servlet-name>
                <url-pattern>/books/*</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>default</servlet-name>
                <url-pattern>/</url-pattern>
              </servlet-mapping>
              <session-config>
                <session-timeout>45</session-timeout>
                <cookie-config>
                  <name>LIBSESSION</name>
                  <http-only>true</http-only>
                </cookie-config>
                <tracking-mode>COOKIE</tracking-mode>
              </session-config>
              <mime-mapping>
                <extension>html</extension>
                <mime-type>text/html</mime-type>
              </mime-mapping>
              <mime-mapping>
                <extension>marc</extension>
                <mime-type>application/marc</mime-type>
              </mime-mapping>
              <welcome-file-list>
                <welcome-file>welcome.html</welcome-file>
              </welcome-file-list>
              <error-page>
                <error-code>404</error-code>
                <location>/not-found.html</location>
              </error-page>
            </web-app>
            """.formatted(LendingLibraryDescriptorDeploymentTest.class.getName());

    private static final String WEB_FRAGMENT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-fragment xmlns="https://jakarta.ee/xml/ns/jakartaee"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                            https://jakarta.ee/xml/ns/jakartaee/web-fragment_6_1.xsd"
                          version="6.1">
              <name>auditTrail</name>
              <filter>
                <filter-name>auditTrail</filter-name>
                <filter-class>%1$s$AuditTrailFilter</filter-class>
                <init-param>
                  <param-name>header-name</param-name>
                  <param-value>X-Audit-Seq</param-value>
                </init-param>
              </filter>
              <filter-mapping>
                <filter-name>auditTrail</filter-name>
                <url-pattern>/*</url-pattern>
              </filter-mapping>
            </web-fragment>
            """.formatted(LendingLibraryDescriptorDeploymentTest.class.getName());

    private static final String CONTEXT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Context>
              <Parameter name="library.lateFeeCents" value="25" override="false"/>
              <CookieProcessor className="org.apache.tomcat.util.http.Rfc6265CookieProcessor"
                               sameSiteCookies="strict"/>
              <JarScanner scanClassPath="false"/>
            </Context>
            """;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Tomcat tomcat;

    private Context context;

    private Path baseDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("lending-library-tomcat");
        Path docBase = Files.createDirectories(baseDir.resolve("library"));
        write(docBase.resolve("WEB-INF/web.xml"), WEB_XML);
        write(docBase.resolve("META-INF/context.xml"), CONTEXT_XML);
        write(docBase.resolve("welcome.html"), "<h1>Riverside Lending Library</h1>");
        write(docBase.resolve("not-found.html"), "<p>Not on our shelves</p>");
        write(docBase.resolve("records/moby-dick.marc"), "=245 10$aMoby-Dick /$cHerman Melville.");

        Path auditJar = Files.createDirectories(docBase.resolve("WEB-INF/lib")).resolve("audit-trail.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(auditJar))) {
            addEntry(jar, "META-INF/web-fragment.xml", WEB_FRAGMENT_XML);
            addEntry(jar, "META-INF/services/" + ServletContainerInitializer.class.getName(),
                    HoldsDeskInitializer.class.getName() + "\n");
        }

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getConnector();
        context = tomcat.addWebapp("/library", docBase.toString());

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/library";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void buildsTheContextFromTheDescriptorsBeforeTheFirstRequest() {
        assertThat(context.getDisplayName()).isEqualTo("Lending Library");
        assertThat(context.getSessionTimeout()).isEqualTo(45);
        assertThat(context.findMimeMapping("marc")).isEqualTo("application/marc");
        assertThat(context.findWelcomeFiles()).containsExactly("welcome.html");
        assertThat(context.findErrorPage(404).getLocation()).isEqualTo("/not-found.html");

        Wrapper catalog = (Wrapper) context.findChild("catalog");
        assertThat(catalog.getServletClass()).isEqualTo(CatalogServlet.class.getName());
        assertThat(catalog.getLoadOnStartup()).isEqualTo(1);
        assertThat(catalog.findInitParameter("page-size")).isEqualTo("2");
        assertThat(context.findServletMapping("/books/*")).isEqualTo("catalog");
        assertThat(context.findChild("holds")).isNotNull();

        assertThat(Arrays.stream(context.findFilterDefs()).map(FilterDef::getFilterName))
                .contains("headerSecurity", "auditTrail");

        ServletContext servletContext = context.getServletContext();
        assertThat(servletContext.getSessionCookieConfig().getName()).isEqualTo("LIBSESSION");
        assertThat(servletContext.getSessionCookieConfig().isHttpOnly()).isTrue();
        assertThat(servletContext.getAttribute(OpeningHoursListener.GREETING))
                .isEqualTo("Riverside branch, late fee 25 cents a day");
        assertThat(servletContext.getAttribute(CatalogServlet.READY)).isEqualTo(2);
        assertThat(servletContext.getAttribute(HoldsDeskInitializer.REGISTERED_BY))
                .isEqualTo("Lending Library");
    }

    @Test
    void servesTheCatalogServletWithDescriptorParametersThroughBothFilterSources() throws Exception {
        HttpResponse<String> firstPage = send(get("/books"));
        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(firstPage.headers().firstValue("Content-Type")).hasValue("text/plain;charset=UTF-8");
        assertThat(firstPage.body()).isEqualTo("""
                Riverside branch, late fee 25 cents a day
                page 1 of 3: Moby-Dick, Middlemarch
                """);
        assertThat(firstPage.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(firstPage.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(firstPage.headers().firstValue("Strict-Transport-Security")).isEmpty();
        int firstSequence = Integer.parseInt(firstPage.headers().firstValue("X-Audit-Seq").orElseThrow());

        HttpResponse<String> lastPage = send(get("/books?page=3"));
        assertThat(lastPage.body()).endsWith("page 3 of 3: Emma\n");
        assertThat(lastPage.headers().firstValue("X-Audit-Seq"))
                .hasValue(Integer.toString(firstSequence + 1));

        HttpResponse<String> single = send(get("/books/4"));
        assertThat(single.statusCode()).isEqualTo(200);
        assertThat(single.body()).isEqualTo("Dune\n");

        HttpResponse<String> unknown = send(get("/books/99"));
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.body()).isEqualTo("<p>Not on our shelves</p>");
    }

    @Test
    void decodesFormsAndIssuesTheConfiguredSessionCookieFromTheInitializerServlet() throws Exception {
        String patron = "Zo\u00eb Ng\u00f4";
        HttpResponse<String> hold = send(HttpRequest.newBuilder(URI.create(baseUrl + "/holds"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "patron=Zo%C3%AB+Ng%C3%B4&title=Beloved")));

        assertThat(hold.statusCode()).isEqualTo(201);
        assertThat(hold.headers().firstValue("Content-Type")).hasValue("text/plain;charset=UTF-8");
        assertThat(hold.body()).isEqualTo("Hold 1 placed for " + patron + ": Beloved (session 2700s)\n");
        assertThat(hold.headers().firstValue("X-Frame-Options")).hasValue("DENY");

        String setCookie = hold.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("LIBSESSION=")
                .contains("Path=/library", "HttpOnly", "SameSite=Strict");
        String sessionCookie = setCookie.substring(0, setCookie.indexOf(';'));

        HttpResponse<String> second = send(HttpRequest.newBuilder(URI.create(baseUrl + "/holds"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", sessionCookie)
                .POST(HttpRequest.BodyPublishers.ofString("patron=Zo%C3%AB+Ng%C3%B4&title=Emma")));
        assertThat(second.body()).isEqualTo("Hold 2 placed for " + patron + ": Emma (session 2700s)\n");
        assertThat(second.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void servesStaticFilesWithDescriptorMimeTypesWelcomeFileAndErrorPage() throws Exception {
        HttpResponse<String> home = send(get("/"));
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/html"));
        assertThat(home.body()).isEqualTo("<h1>Riverside Lending Library</h1>");

        HttpResponse<String> record = send(get("/records/moby-dick.marc"));
        assertThat(record.statusCode()).isEqualTo(200);
        assertThat(record.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/marc"));
        assertThat(record.body()).startsWith("=245 10$aMoby-Dick");

        HttpResponse<String> missing = send(get("/records/war-and-peace.marc"));
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).isEqualTo("<p>Not on our shelves</p>");

        HttpResponse<String> descriptor = send(get("/WEB-INF/web.xml"));
        assertThat(descriptor.statusCode()).isEqualTo(404);
        assertThat(descriptor.body()).isEqualTo("<p>Not on our shelves</p>");
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void addEntry(JarOutputStream jar, String name, String content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }

    /// Composes the branch greeting from the `web.xml` context parameter and the `context.xml` one.
    public static class OpeningHoursListener implements ServletContextListener {

        static final String GREETING = "library.greeting";

        @Override
        public void contextInitialized(ServletContextEvent event) {
            ServletContext servletContext = event.getServletContext();
            servletContext.setAttribute(GREETING, servletContext.getInitParameter("library.branch")
                    + " branch, late fee " + servletContext.getInitParameter("library.lateFeeCents")
                    + " cents a day");
        }
    }

    /// Pages through the shelf with the page size its `web.xml` init parameter sets.
    public static class CatalogServlet extends HttpServlet {

        static final String READY = "library.catalogPageSize";

        private int pageSize;

        @Override
        public void init() {
            pageSize = Integer.parseInt(getInitParameter("page-size"));
            getServletContext().setAttribute(READY, pageSize);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.length() > 1) {
                int number = Integer.parseInt(pathInfo.substring(1));
                if (number < 1 || number > SHELF.size()) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                response.setContentType("text/plain");
                response.getWriter().println(SHELF.get(number - 1));
                return;
            }
            String pageParameter = request.getParameter("page");
            int page = pageParameter == null ? 1 : Integer.parseInt(pageParameter);
            int pages = (SHELF.size() + pageSize - 1) / pageSize;
            List<String> books = SHELF.subList((page - 1) * pageSize, Math.min(page * pageSize, SHELF.size()));
            response.setContentType("text/plain");
            response.getWriter().println(getServletContext().getAttribute(OpeningHoursListener.GREETING));
            response.getWriter().println("page " + page + " of " + pages + ": " + String.join(", ", books));
        }
    }

    /// Numbers every request in a header whose name comes from the fragment's init parameter.
    public static class AuditTrailFilter implements Filter {

        private final AtomicInteger sequence = new AtomicInteger();

        private String headerName;

        @Override
        public void init(FilterConfig filterConfig) {
            headerName = filterConfig.getInitParameter("header-name");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            ((HttpServletResponse) response).setHeader(headerName, Integer.toString(sequence.incrementAndGet()));
            chain.doFilter(request, response);
        }
    }

    /// Registers the holds desk from the audit JAR the way a pluggable library does.
    public static class HoldsDeskInitializer implements ServletContainerInitializer {

        static final String REGISTERED_BY = "library.holdsRegisteredBy";

        @Override
        public void onStartup(Set<Class<?>> classes, ServletContext servletContext) {
            ServletRegistration.Dynamic holds = servletContext.addServlet("holds", HoldsDeskServlet.class);
            holds.addMapping("/holds");
            servletContext.setAttribute(REGISTERED_BY, servletContext.getServletContextName());
        }
    }

    /// Places holds for a patron and counts them in the patron's session.
    public static class HoldsDeskServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession();
            AtomicInteger holds = (AtomicInteger) session.getAttribute("holds");
            if (holds == null) {
                holds = new AtomicInteger();
                session.setAttribute("holds", holds);
            }
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setContentType("text/plain");
            response.getWriter().println("Hold " + holds.incrementAndGet() + " placed for "
                    + request.getParameter("patron") + ": " + request.getParameter("title")
                    + " (session " + session.getMaxInactiveInterval() + "s)");
        }
    }
}
