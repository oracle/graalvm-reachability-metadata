/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Monitors and controls a running server through Tomcat's JMX management, where the modeler
 * `Registry` publishes the engine's connector, thread pool, request processor, web modules,
 * servlets and session managers as model MBeans on the platform `MBeanServer`.
 *
 * A warehouse runs its inventory and picking applications on one embedded Tomcat whose engine is
 * named `Warehouse`, so every component lives in the `Warehouse` JMX domain. The operations
 * console finds the deployed web modules, reads the connector's bound port, watches request and
 * error counts per servlet and for the whole connector, lists and inspects stock reservations
 * held in sessions and expires a stuck one, raises the thread pool limit during a stock-take
 * while seeing a busy worker, and takes the inventory application offline and back while the
 * picking application keeps serving, after enabling session persistence on the manager so the
 * reservations survive the restart.
 */
class WarehouseOpsJmxManagementTest {

    private static final String DOMAIN = "Warehouse";

    private static final String RESERVATION_ATTRIBUTE = "warehouse.reservation";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final MBeanServer mbeans = ManagementFactory.getPlatformMBeanServer();

    private final List<String> destroyedSessions = new CopyOnWriteArrayList<>();

    private final CountDownLatch stockTakeStarted = new CountDownLatch(1);

    private final CountDownLatch stockTakeRelease = new CountDownLatch(1);

    private Path baseDir;

    private Tomcat tomcat;

    private String baseUrl;

    @BeforeEach
    void startWarehouse() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("warehouse-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getEngine().setName(DOMAIN);
        tomcat.getConnector();

        Context inventory = tomcat.addContext("/inventory", baseDir.toString());
        inventory.setDisplayName("Inventory");
        inventory.setSessionTimeout(20);
        inventory.addLifecycleListener(new InventoryConfig(destroyedSessions, stockTakeStarted, stockTakeRelease));

        Context picking = tomcat.addContext("/picking", baseDir.toString());
        picking.setDisplayName("Picking");
        Tomcat.addServlet(picking, "waves", new PickingWaveServlet());
        picking.addServletMappingDecoded("/waves", "waves");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort();
    }

    @AfterEach
    void stopWarehouse() throws LifecycleException, IOException {
        stockTakeRelease.countDown();
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void publishesTheRunningServerInTheEngineDomain() throws Exception {
        Set<String> modules = mbeans.queryNames(new ObjectName(DOMAIN + ":j2eeType=WebModule,*"), null).stream()
                .map(name -> name.getKeyProperty("name"))
                .collect(Collectors.toSet());
        assertThat(modules).containsExactlyInAnyOrder("//localhost/inventory", "//localhost/picking");

        ObjectName inventory = webModule("inventory");
        assertThat(mbeans.getAttribute(inventory, "displayName")).isEqualTo("Inventory");
        assertThat(mbeans.getAttribute(inventory, "path")).isEqualTo("/inventory");
        assertThat(mbeans.getAttribute(inventory, "sessionTimeout")).isEqualTo(20);
        assertThat(mbeans.getAttribute(inventory, "stateName")).isEqualTo("STARTED");
        assertThat((String[]) mbeans.invoke(inventory, "findServletMappings", null, null))
                .containsExactlyInAnyOrder("/stock", "/reservations", "/stock-take");

        ObjectName connector = single(DOMAIN + ":type=Connector,*");
        assertThat(mbeans.getAttribute(connector, "localPort")).isEqualTo(tomcat.getConnector().getLocalPort());
        assertThat(mbeans.getAttribute(connector, "scheme")).isEqualTo("http");
        assertThat(mbeans.getAttribute(connector, "stateName")).isEqualTo("STARTED");

        ObjectName stock = servlet("inventory", "stock");
        assertThat(mbeans.getAttribute(stock, "servletClass")).isEqualTo(StockServlet.class.getName());
        assertThat((String[]) mbeans.invoke(stock, "findMappings", null, null)).containsExactly("/stock");
    }

    @Test
    void countsRequestsAndErrorsPerServletAndForTheConnector() throws Exception {
        for (String sku : List.of("PALLET-1", "PALLET-2", "CRATE-7", "CRATE-8")) {
            HttpResponse<String> stock = send(get("/inventory/stock?sku=" + sku));
            assertThat(stock.statusCode()).isEqualTo(200);
            assertThat(stock.body()).isEqualTo(sku + "=" + sku.length());
        }
        HttpResponse<String> unknown = send(get("/inventory/stock?sku=GHOST"));
        assertThat(unknown.statusCode()).isEqualTo(500);
        assertThat(send(get("/picking/waves")).body()).isEqualTo("wave-1,wave-2");

        ObjectName stock = servlet("inventory", "stock");
        assertThat(count(stock, "requestCount")).isEqualTo(5L);
        assertThat(count(stock, "errorCount")).isEqualTo(1L);
        assertThat(count(webModule("inventory"), "requestCount")).isEqualTo(5L);
        assertThat(count(webModule("inventory"), "errorCount")).isEqualTo(1L);
        assertThat(count(webModule("picking"), "requestCount")).isEqualTo(1L);
        assertThat(count(webModule("picking"), "errorCount")).isEqualTo(0L);

        ObjectName requestProcessor = single(DOMAIN + ":type=GlobalRequestProcessor,*");
        awaitCount(requestProcessor, "requestCount", 6L);
        assertThat(count(requestProcessor, "errorCount")).isEqualTo(1L);
        assertThat(count(requestProcessor, "bytesSent")).isPositive();

        mbeans.invoke(requestProcessor, "resetCounters", null, null);
        assertThat(count(requestProcessor, "requestCount")).isEqualTo(0L);
        assertThat(count(requestProcessor, "errorCount")).isEqualTo(0L);
    }

    @Test
    void inspectsAndExpiresReservationsThroughTheSessionManager() throws Exception {
        String forklift = reserve("FORKLIFT-3");
        String dock = reserve("DOCK-B");
        String scanner = reserve("SCANNER-12");

        ObjectName manager = new ObjectName(DOMAIN + ":type=Manager,host=localhost,context=/inventory");
        assertThat(mbeans.getAttribute(manager, "activeSessions")).isEqualTo(3);
        assertThat(count(manager, "sessionCounter")).isEqualTo(3L);
        assertThat(mbeans.getAttribute(manager, "stateName")).isEqualTo("STARTED");
        String listed = (String) mbeans.invoke(manager, "listSessionIds", null, null);
        assertThat(Arrays.asList(listed.trim().split(" "))).containsExactlyInAnyOrder(forklift, dock, scanner);
        String[] signature = {String.class.getName(), String.class.getName()};
        assertThat(mbeans.invoke(manager, "getSessionAttribute", new Object[] {dock, RESERVATION_ATTRIBUTE}, signature))
                .isEqualTo("DOCK-B");

        mbeans.invoke(manager, "expireSession", new Object[] {dock}, new String[] {String.class.getName()});

        assertThat(destroyedSessions).containsExactly(dock + ":DOCK-B");
        assertThat(mbeans.getAttribute(manager, "activeSessions")).isEqualTo(2);
        assertThat(count(manager, "expiredSessions")).isEqualTo(1L);
        assertThat((String) mbeans.invoke(manager, "listSessionIds", null, null)).doesNotContain(dock);
        assertThat(send(withSession(get("/inventory/reservations"), dock)).body()).isEqualTo("none");
        assertThat(send(withSession(get("/inventory/reservations"), forklift)).body()).isEqualTo("FORKLIFT-3");
    }

    @Test
    void raisesTheThreadPoolLimitAndSeesABusyWorker() throws Exception {
        ObjectName threadPool = single(DOMAIN + ":type=ThreadPool,*");
        ObjectName connector = single(DOMAIN + ":type=Connector,*");
        assertThat(mbeans.getAttribute(threadPool, "running")).isEqualTo(true);
        assertThat(mbeans.getAttribute(threadPool, "localPort")).isEqualTo(tomcat.getConnector().getLocalPort());

        mbeans.setAttribute(threadPool, new Attribute("maxThreads", 64));

        assertThat(mbeans.getAttribute(threadPool, "maxThreads")).isEqualTo(64);
        assertThat(mbeans.getAttribute(connector, "maxThreads")).isEqualTo(64);

        CompletableFuture<HttpResponse<String>> stockTake = client.sendAsync(
                get("/inventory/stock-take").timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(stockTakeStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(count(threadPool, "currentThreadsBusy")).isGreaterThanOrEqualTo(1L);
        assertThat(count(threadPool, "connectionCount")).isGreaterThanOrEqualTo(1L);

        stockTakeRelease.countDown();
        HttpResponse<String> done = stockTake.get(10, TimeUnit.SECONDS);
        assertThat(done.statusCode()).isEqualTo(200);
        assertThat(done.body()).isEqualTo("stock-take complete");
    }

    @Test
    void takesTheInventoryOfflineAndBackWhilePickingKeepsServing() throws Exception {
        String pallet = reserve("PALLET-9");
        ObjectName inventory = webModule("inventory");
        ObjectName manager = new ObjectName(DOMAIN + ":type=Manager,host=localhost,context=/inventory");
        Path sessionStore = baseDir.resolve("work/Warehouse/localhost/inventory/RESERVATIONS.ser");
        assertThat(mbeans.getAttribute(manager, "pathname")).isNull();
        mbeans.setAttribute(manager, new Attribute("pathname", "RESERVATIONS.ser"));

        mbeans.invoke(inventory, "stop", null, null);

        assertThat(mbeans.getAttribute(inventory, "stateName")).isEqualTo("STOPPED");
        assertThat(mbeans.getAttribute(manager, "stateName")).isEqualTo("STOPPED");
        assertThat(sessionStore).isRegularFile();
        assertThat(send(get("/inventory/stock?sku=PALLET-1")).statusCode()).isEqualTo(404);
        assertThat(send(get("/picking/waves")).body()).isEqualTo("wave-1,wave-2");

        mbeans.invoke(inventory, "start", null, null);

        assertThat(mbeans.getAttribute(inventory, "stateName")).isEqualTo("STARTED");
        assertThat((String[]) mbeans.invoke(inventory, "findServletMappings", null, null))
                .containsExactlyInAnyOrder("/stock", "/reservations", "/stock-take");
        assertThat(sessionStore).doesNotExist();
        assertThat(mbeans.getAttribute(manager, "activeSessions")).isEqualTo(1);
        assertThat(destroyedSessions).isEmpty();
        HttpResponse<String> stock = send(get("/inventory/stock?sku=PALLET-1"));
        assertThat(stock.statusCode()).isEqualTo(200);
        assertThat(stock.body()).isEqualTo("PALLET-1=8");
        assertThat(send(withSession(get("/inventory/reservations"), pallet)).body()).isEqualTo("PALLET-9");
    }

    private String reserve(String sku) throws IOException, InterruptedException {
        HttpResponse<String> reserved = send(HttpRequest.newBuilder(URI.create(baseUrl + "/inventory/reservations"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("sku=" + sku)));
        assertThat(reserved.statusCode()).isEqualTo(201);
        return reserved.body();
    }

    private ObjectName webModule(String context) throws JMException {
        return single(DOMAIN + ":j2eeType=WebModule,name=//localhost/" + context + ",*");
    }

    private ObjectName servlet(String context, String name) throws JMException {
        return single(DOMAIN + ":j2eeType=Servlet,WebModule=//localhost/" + context + ",name=" + name + ",*");
    }

    private ObjectName single(String pattern) throws JMException {
        Set<ObjectName> names = mbeans.queryNames(new ObjectName(pattern), null);
        assertThat(names).as(pattern).hasSize(1);
        return names.iterator().next();
    }

    /** Reads a statistics counter, which Tomcat reports as an `int` or a `long` depending on the component. */
    private long count(ObjectName name, String attribute) throws JMException {
        return ((Number) mbeans.getAttribute(name, attribute)).longValue();
    }

    /** Polls a counter the connector updates once the response has been written, for at most ten seconds. */
    private void awaitCount(ObjectName name, String attribute, long expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (count(name, attribute) != expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(count(name, attribute)).isEqualTo(expected);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private static HttpRequest.Builder withSession(HttpRequest.Builder request, String sessionId) {
        return request.header("Cookie", "JSESSIONID=" + sessionId);
    }

    /**
     * Configures the inventory application each time its context starts, as `ContextConfig` does for a
     * deployed web application: a context stopped through JMX drops its servlets and listeners, so
     * they are added again on every `configure_start` rather than once before the server starts.
     */
    static final class InventoryConfig implements LifecycleListener {

        private final SessionLog sessionLog;

        private final StockTakeServlet stockTake;

        InventoryConfig(List<String> destroyedSessions, CountDownLatch stockTakeStarted,
                CountDownLatch stockTakeRelease) {
            this.sessionLog = new SessionLog(destroyedSessions);
            this.stockTake = new StockTakeServlet(stockTakeStarted, stockTakeRelease);
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (!event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
                return;
            }
            Context context = (Context) event.getLifecycle();
            Tomcat.addServlet(context, "stock", new StockServlet());
            context.addServletMappingDecoded("/stock", "stock");
            Tomcat.addServlet(context, "reservations", new ReservationServlet());
            context.addServletMappingDecoded("/reservations", "reservations");
            Tomcat.addServlet(context, "stockTake", stockTake);
            context.addServletMappingDecoded("/stock-take", "stockTake");
            context.addServletContainerInitializer(
                    (classes, servletContext) -> servletContext.addListener(sessionLog), null);
        }
    }

    /** Records each reservation session the container destroys, for the operations audit. */
    static final class SessionLog implements HttpSessionListener {

        private final List<String> destroyed;

        SessionLog(List<String> destroyed) {
            this.destroyed = destroyed;
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent event) {
            HttpSession session = event.getSession();
            destroyed.add(session.getId() + ":" + session.getAttribute(RESERVATION_ATTRIBUTE));
        }
    }

    /** Reports the stock level of a known SKU and fails for an unknown one. */
    static final class StockServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            String sku = request.getParameter("sku");
            if (sku.equals("GHOST")) {
                throw new ServletException("No stock record for " + sku);
            }
            response.setContentType("text/plain");
            response.getWriter().write(sku + "=" + sku.length());
        }
    }

    /** Holds one stock reservation per visitor session. */
    static final class ReservationServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession(false);
            response.setContentType("text/plain");
            response.getWriter().write(session == null ? "none" : (String) session.getAttribute(RESERVATION_ATTRIBUTE));
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession(true);
            session.setAttribute(RESERVATION_ATTRIBUTE, request.getParameter("sku"));
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setContentType("text/plain");
            response.getWriter().write(session.getId());
        }
    }

    /** Runs a stock-take that keeps its worker thread until the warehouse signals it is counted. */
    static final class StockTakeServlet extends HttpServlet {

        private final transient CountDownLatch started;

        private final transient CountDownLatch release;

        StockTakeServlet(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            started.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            response.setContentType("text/plain");
            response.getWriter().write("stock-take complete");
        }
    }

    /** Lists the picking waves of the companion application. */
    static final class PickingWaveServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.getWriter().write("wave-1,wave-2");
        }
    }
}
