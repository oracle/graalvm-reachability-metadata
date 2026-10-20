/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wires an order desk through Tomcat's JNDI naming, where `Tomcat.enableNaming()` publishes a
 * DBCP `ContextResource` over H2, `ContextEnvironment` entries and a `ContextResourceLink` to a
 * server-wide global environment under `java:comp/env` for the servlets of one `Context`.
 *
 * A wholesaler runs its order desk embedded in a plain Java process but keeps the classic
 * container-managed configuration: the servlet never builds its own connection pool or reads a
 * properties file, it looks up `jdbc/orders`, its currency and line limit, and the region the
 * server operator configured globally. Clients place orders as JSON, list them back, and get
 * `422` when an order exceeds the configured line limit. The servlet reports the pool settings
 * the resource factory applied and that the application naming context is read only, while code
 * outside the web application finds nothing bound under `java:comp`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JndiOrderDeskNamingTest {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private Tomcat tomcat;

    private Path baseDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("order-desk-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        tomcat.enableNaming();

        ContextEnvironment region = new ContextEnvironment();
        region.setName("shop/region");
        region.setType(String.class.getName());
        region.setValue("EU-West");
        tomcat.getServer().getGlobalNamingResources().addEnvironment(region);

        Context context = tomcat.addContext("/desk", baseDir.toString());

        ContextResource ordersDb = new ContextResource();
        ordersDb.setName("jdbc/orders");
        ordersDb.setType(DataSource.class.getName());
        ordersDb.setAuth("Container");
        ordersDb.setProperty("factory", "org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory");
        ordersDb.setProperty("driverClassName", "org.h2.Driver");
        ordersDb.setProperty("url", "jdbc:h2:mem:order-desk;DB_CLOSE_DELAY=-1");
        ordersDb.setProperty("username", "sa");
        ordersDb.setProperty("password", "");
        ordersDb.setProperty("maxTotal", "4");
        ordersDb.setProperty("validationQuery", "SELECT 1");
        context.getNamingResources().addResource(ordersDb);

        context.getNamingResources().addEnvironment(environment("desk/currency", String.class, "EUR"));
        context.getNamingResources().addEnvironment(environment("desk/maxLines", Integer.class, "3"));

        ContextResourceLink regionLink = new ContextResourceLink();
        regionLink.setName("desk/region");
        regionLink.setGlobal("shop/region");
        regionLink.setType(String.class.getName());
        context.getNamingResources().addResourceLink(regionLink);

        Wrapper wrapper = Tomcat.addServlet(context, "orders", new OrderDeskServlet());
        wrapper.setLoadOnStartup(1);
        context.addServletMappingDecoded("/orders", "orders");
        context.addServletMappingDecoded("/naming", "orders");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/desk";
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
    void placesAndListsOrdersThroughTheJndiDataSource() throws Exception {
        HttpResponse<String> placed = post("""
                {"customer": "Bakery Lindqvist", "lines": ["flour", "yeast"], "totalCents": 4250}
                """);

        assertThat(placed.statusCode()).isEqualTo(201);
        JsonNode receipt = mapper.readTree(placed.body());
        assertThat(receipt.get("id").asLong()).isPositive();
        assertThat(receipt.get("currency").asText()).isEqualTo("EUR");
        assertThat(receipt.get("region").asText()).isEqualTo("EU-West");
        assertThat(placed.headers().firstValue("Location"))
                .hasValue("/desk/orders?id=" + receipt.get("id").asLong());

        HttpResponse<String> listed = get("/orders");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        JsonNode orders = mapper.readTree(listed.body()).get("orders");
        assertThat(orders.isArray()).isTrue();
        JsonNode bakery = null;
        for (JsonNode order : orders) {
            if (order.get("id").asLong() == receipt.get("id").asLong()) {
                bakery = order;
            }
        }
        assertThat(bakery).isNotNull();
        assertThat(bakery.get("customer").asText()).isEqualTo("Bakery Lindqvist");
        assertThat(bakery.get("lineCount").asInt()).isEqualTo(2);
        assertThat(bakery.get("total").asText()).isEqualTo("42.50 EUR");
    }

    @Test
    void rejectsOrdersAboveTheLineLimitFromTheEnvironmentEntry() throws Exception {
        HttpResponse<String> rejected = post("""
                {"customer": "Cafe Moreau", "lines": ["milk", "beans", "cups", "lids"], "totalCents": 990}
                """);

        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(mapper.readTree(rejected.body()).get("error").asText())
                .isEqualTo("at most 3 lines per order");

        JsonNode orders = mapper.readTree(get("/orders").body()).get("orders");
        for (JsonNode order : orders) {
            assertThat(order.get("customer").asText()).isNotEqualTo("Cafe Moreau");
        }
    }

    @Test
    void exposesTheNamingConfigurationTheContainerBound() throws Exception {
        HttpResponse<String> response = get("/naming");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode naming = mapper.readTree(response.body());
        assertThat(naming.get("dataSourceClass").asText())
                .isEqualTo("org.apache.tomcat.dbcp.dbcp2.BasicDataSource");
        assertThat(naming.get("maxTotal").asInt()).isEqualTo(4);
        assertThat(naming.get("validationQuery").asText()).isEqualTo("SELECT 1");
        assertThat(naming.get("maxLinesType").asText()).isEqualTo("java.lang.Integer");
        assertThat(naming.get("region").asText()).isEqualTo("EU-West");
        assertThat(naming.get("sameDataSourceOnEveryLookup").asBoolean()).isTrue();
        assertThat(naming.get("readOnly").asBoolean()).isTrue();
    }

    @Test
    void codeOutsideTheWebApplicationSeesNoComponentEnvironment() {
        assertThatThrownBy(() -> new InitialContext().lookup("java:comp/env/jdbc/orders"))
                .isInstanceOf(NamingException.class);
    }

    private static ContextEnvironment environment(String name, Class<?> type, String value) {
        ContextEnvironment environment = new ContextEnvironment();
        environment.setName(name);
        environment.setType(type.getName());
        environment.setValue(value);
        return environment;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    private HttpResponse<String> post(String json) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Places and lists orders using only what the container publishes under `java:comp/env`. */
    static final class OrderDeskServlet extends HttpServlet {

        private static final String ENV = "java:comp/env/";

        private final ObjectMapper mapper = new ObjectMapper();

        private DataSource dataSource;

        private String currency;

        private int maxLines;

        private String region;

        @Override
        public void init() throws ServletException {
            try {
                InitialContext initial = new InitialContext();
                dataSource = (DataSource) initial.lookup(ENV + "jdbc/orders");
                currency = (String) initial.lookup(ENV + "desk/currency");
                maxLines = (Integer) initial.lookup(ENV + "desk/maxLines");
                region = (String) initial.lookup(ENV + "desk/region");
            } catch (NamingException e) {
                throw new ServletException("Order desk naming is not configured", e);
            }
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS orders (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            customer VARCHAR(200) NOT NULL,
                            line_count INT NOT NULL,
                            total_cents BIGINT NOT NULL
                        )
                        """);
            } catch (SQLException e) {
                throw new ServletException("Cannot create the orders table", e);
            }
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            if (request.getServletPath().equals("/naming")) {
                writeJson(response, HttpServletResponse.SC_OK, describeNaming());
                return;
            }
            ObjectNode body = mapper.createObjectNode();
            ArrayNode orders = body.putArray("orders");
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT id, customer, line_count, total_cents FROM orders ORDER BY id")) {
                while (rows.next()) {
                    long cents = rows.getLong("total_cents");
                    orders.addObject()
                            .put("id", rows.getLong("id"))
                            .put("customer", rows.getString("customer"))
                            .put("lineCount", rows.getInt("line_count"))
                            .put("total", String.format("%d.%02d %s", cents / 100, cents % 100, currency));
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
            writeJson(response, HttpServletResponse.SC_OK, body);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            JsonNode order = mapper.readTree(request.getInputStream());
            int lineCount = order.get("lines").size();
            if (lineCount > maxLines) {
                ObjectNode error = mapper.createObjectNode().put("error", "at most " + maxLines + " lines per order");
                writeJson(response, 422, error);
                return;
            }
            long id;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO orders (customer, line_count, total_cents) VALUES (?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, order.get("customer").asText());
                insert.setInt(2, lineCount);
                insert.setLong(3, order.get("totalCents").asLong());
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
            response.setHeader("Location", request.getContextPath() + "/orders?id=" + id);
            writeJson(response, HttpServletResponse.SC_CREATED, mapper.createObjectNode()
                    .put("id", id)
                    .put("currency", currency)
                    .put("region", region));
        }

        private ObjectNode describeNaming() throws ServletException {
            try {
                InitialContext initial = new InitialContext();
                Object pool = initial.lookup(ENV + "jdbc/orders");
                BasicDataSource basic = (BasicDataSource) pool;
                boolean readOnly;
                try {
                    initial.bind(ENV + "desk/injected", "not allowed");
                    readOnly = false;
                } catch (OperationNotSupportedException e) {
                    readOnly = true;
                }
                return mapper.createObjectNode()
                        .put("dataSourceClass", pool.getClass().getName())
                        .put("maxTotal", basic.getMaxTotal())
                        .put("validationQuery", basic.getValidationQuery())
                        .put("maxLinesType", initial.lookup(ENV + "desk/maxLines").getClass().getName())
                        .put("region", (String) initial.lookup(ENV + "desk/region"))
                        .put("sameDataSourceOnEveryLookup", pool == dataSource)
                        .put("readOnly", readOnly);
            } catch (NamingException e) {
                throw new ServletException(e);
            }
        }

        private void writeJson(HttpServletResponse response, int status, JsonNode body) throws IOException {
            response.setStatus(status);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            mapper.writeValue(response.getOutputStream(), body);
        }
    }
}
