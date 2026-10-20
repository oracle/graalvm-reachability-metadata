/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.h2.api.ErrorCode;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The H2 Console web server: the browser-facing console started with
 * `org.h2.tools.Server.createWebServer`, its HTTP session handshake, connection test and login
 * forms, the schema tree, SQL execution with HTML result tables and `@meta` commands, SQL
 * auto-completion, the guard against creating databases remotely, saved connection settings
 * and admin preferences kept in the `-properties` directory, and localized pages.
 *
 * A stockroom keeps its inventory in an in-memory database that the warehouse application
 * holds open, and the shift supervisor inspects and corrects stock through the H2 Console from
 * a browser on the same machine, as an HTTP client here.
 */
public class StockroomWebConsoleTest {

    private static final String SCHEMA = """
            CREATE TABLE stock_item (
                sku VARCHAR(12) PRIMARY KEY,
                title VARCHAR(40) NOT NULL,
                on_hand INT NOT NULL CHECK (on_hand >= 0)
            );
            INSERT INTO stock_item VALUES
                ('LMP-01', 'Desk lamp', 14),
                ('CHR-07', 'Office chair', 3),
                ('SHF-22', 'Steel shelf', 9);
            """;

    private static final Pattern RESULT_TABLE = Pattern.compile(
            "<table class=\"resultSet\"[^>]*>(.*?)</table>", Pattern.DOTALL);

    private static final Pattern CELL = Pattern.compile("<t[hd]>(.*?)</t[hd]>");

    private static final Pattern ROW = Pattern.compile("<tr>(.*?)</tr>");

    @TempDir
    Path propertiesDir;

    @Test
    void supervisorBrowsesSchemaAndCorrectsStockThroughTheConsole() throws Exception {
        String url = "jdbc:h2:mem:stockroom-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection application = DriverManager.getConnection(url, "keeper", "crate-42");
                Statement statement = application.createStatement()) {
            statement.execute(SCHEMA);
            Server server = startConsole();
            try {
                assertTrue(server.isRunning(true));
                assertTrue(server.getStatus().contains("only local connections"));
                Console console = Console.open(server);

                String test = console.post("test.do", "driver", "org.h2.Driver", "url", url,
                        "user", "keeper", "password", "crate-42");
                assertTrue(test.contains("<div class=\"success\">Test successful</div>"));

                String rejected = console.post("login.do", "driver", "org.h2.Driver", "url", url,
                        "user", "keeper", "password", "wrong");
                assertTrue(rejected.contains("Wrong user name or password"));
                assertTrue(rejected.contains("ErrorCode.html#c" + ErrorCode.WRONG_USER_OR_PASSWORD));
                assertTrue(rejected.contains("action=\"login.do?jsessionid=" + console.sessionId));

                String frames = console.post("login.do", "driver", "org.h2.Driver", "url", url,
                        "user", "keeper", "password", "crate-42");
                assertTrue(frames.contains("<frameset"));
                assertTrue(frames.contains("src=\"tables.do?jsessionid=" + console.sessionId));
                assertTrue(frames.contains("src=\"query.jsp?jsessionid=" + console.sessionId));

                String tree = console.get("tables.do");
                assertTrue(tree.contains("'table', 'STOCK_ITEM'"));
                assertTrue(tree.contains("addTable('STOCK_ITEM', 'SKU TITLE ON_HAND', 1);"));
                assertTrue(tree.contains("'column', 'ON_HAND'"));
                assertTrue(tree.contains("'user', 'KEEPER'"));
                assertTrue(tree.contains("'info', 'H2 2.1.210 (2022-01-17)'"));

                String lowStock = console.query(
                        "SELECT sku, title, on_hand FROM stock_item WHERE on_hand < 10 ORDER BY sku");
                assertEquals(List.of(
                        List.of("SKU", "TITLE", "ON_HAND"),
                        List.of("CHR-07", "Office chair", "3"),
                        List.of("SHF-22", "Steel shelf", "9")), resultRows(lowStock));
                assertTrue(lowStock.contains("(2 rows, "));

                String corrected = console.query("UPDATE stock_item SET on_hand = on_hand + 20 "
                        + "WHERE sku = 'CHR-07'");
                assertTrue(corrected.contains("Update count: 1"));
                try (ResultSet rs = statement.executeQuery(
                        "SELECT on_hand FROM stock_item WHERE sku = 'CHR-07'")) {
                    assertTrue(rs.next());
                    assertEquals(23, rs.getInt(1));
                }

                String violation = console.query(
                        "UPDATE stock_item SET on_hand = -1 WHERE sku = 'LMP-01'");
                assertTrue(violation.contains("<div class=\"error\">"));
                assertTrue(violation.contains("ErrorCode.html#c" + ErrorCode.CHECK_CONSTRAINT_VIOLATED_1));

                String missing = console.query("SELECT * FROM stock_movement");
                assertTrue(missing.contains("Table &quot;STOCK_MOVEMENT&quot; not found"));
                assertTrue(missing.contains("ErrorCode.html#c" + ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1));

                List<List<String>> meta = resultRows(console.query("@meta SELECT sku, on_hand FROM stock_item"));
                assertEquals("label", meta.get(0).get(1));
                assertEquals("typeName", meta.get(0).get(7));
                assertEquals(List.of("SKU", "CHARACTER VARYING"), List.of(meta.get(1).get(1), meta.get(1).get(7)));
                assertEquals(List.of("ON_HAND", "INTEGER"), List.of(meta.get(2).get(1), meta.get(2).get(7)));

                String completion = console.get("autoCompleteList.do", "query", "SELECT * FROM STOCK_I");
                assertTrue(completion.contains("#STOCK_ITEM#TEM"), completion);

                String afterLogout = console.get("logout.do");
                assertTrue(afterLogout.contains("login.jsp?jsessionid=" + console.sessionId));
            } finally {
                server.stop();
            }
            assertFalse(server.isRunning(false));
            try (ResultSet rs = statement.executeQuery("SELECT SUM(on_hand) FROM stock_item")) {
                assertTrue(rs.next());
                assertEquals(46, rs.getInt(1));
            }
        }
    }

    @Test
    void consoleCreatesDatabasesOnlyWhenTheServerAllowsIt() throws Exception {
        String name = "receiving-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";

        Server guarded = startConsole();
        try {
            Console console = Console.open(guarded);
            String refused = console.post("login.do", "driver", "org.h2.Driver", "url", url,
                    "user", "sa", "password", "");
            assertTrue(refused.contains("Database &quot;mem:" + name + "&quot; not found"));
            assertTrue(refused.contains("ErrorCode.html#c" + ErrorCode.REMOTE_DATABASE_NOT_FOUND_1));
        } finally {
            guarded.stop();
        }

        Server open = startConsole("-ifNotExists");
        try {
            Console console = Console.open(open);
            assertTrue(console.post("login.do", "driver", "org.h2.Driver", "url", url,
                    "user", "sa", "password", "").contains("<frameset"));
            String created = console.query("""
                    CREATE TABLE pallet (id INT PRIMARY KEY, dock VARCHAR(8));
                    INSERT INTO pallet VALUES (1, 'D1'), (2, 'D4')""");
            assertFalse(created.contains("<div class=\"error\">"), created);
            try (Connection application = DriverManager.getConnection(url, "sa", "");
                    Statement statement = application.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT dock FROM pallet ORDER BY id")) {
                List<String> docks = new ArrayList<>();
                while (rs.next()) {
                    docks.add(rs.getString(1));
                }
                assertEquals(List.of("D1", "D4"), docks);
                statement.execute("SHUTDOWN");
            }
        } finally {
            open.stop();
        }
    }

    @Test
    void savedSettingsAndPreferencesSurviveAConsoleRestart() throws Exception {
        String url = "jdbc:h2:mem:stockroom-" + UUID.randomUUID();
        Server first = startConsole("-webAdminPassword", "dock-master");
        try {
            Console console = Console.open(first);
            String german = console.get("index.do", "language", "de");
            assertTrue(german.contains("login.jsp?jsessionid=" + console.sessionId));
            String login = console.get("login.jsp");
            assertTrue(login.contains("Benutzername:"));
            assertTrue(login.contains("value=\"Verbinden\""));
            assertTrue(login.contains("<option value=\"de\" selected>"));

            console.post("settingSave.do", "name", "Stockroom", "driver", "org.h2.Driver", "url", url,
                    "user", "keeper");

            String locked = console.get("admin.do");
            assertTrue(locked.contains("adminLogin.do?jsessionid=" + console.sessionId));
            assertFalse(locked.contains("name=\"port\""));
            String denied = console.post("adminLogin.do", "password", "not-the-password");
            assertFalse(denied.contains("name=\"port\""));
            String preferences = console.post("adminLogin.do", "password", "dock-master");
            assertTrue(preferences.contains("H2 Console Optionen"));
            assertTrue(preferences.contains("name=\"port\" value=\"" + first.getPort() + "\""));
        } finally {
            first.stop();
        }

        Path properties = propertiesDir.resolve(".h2.server.properties");
        assertTrue(Files.isRegularFile(properties));
        String saved = Files.readString(properties, StandardCharsets.ISO_8859_1);
        assertTrue(saved.contains("Stockroom|org.h2.Driver|" + url.replace(":", "\\:") + "|keeper"), saved);
        assertTrue(saved.contains("webAllowOthers=false"));
        assertTrue(saved.contains("webAdminPassword="));
        assertFalse(saved.contains("dock-master"));

        Server second = startConsole();
        try {
            Console console = Console.open(second);
            String chosen = console.get("index.do", "setting", "Stockroom");
            assertTrue(chosen.contains("login.jsp?jsessionid=" + console.sessionId));
            String login = console.get("login.jsp");
            assertTrue(login.contains("<option value=\"Stockroom\" selected>Stockroom</option>"), login);
            assertTrue(login.contains("name=\"url\" value=\"" + url + "\""));
            assertTrue(login.contains("name=\"user\" value=\"keeper\""));
            assertTrue(console.post("adminLogin.do", "password", "dock-master").contains("name=\"port\""));
        } finally {
            second.stop();
        }
    }

    private Server startConsole(String... options) throws SQLException {
        List<String> args = new ArrayList<>(List.of("-webPort", "0", "-properties", propertiesDir.toString()));
        args.addAll(List.of(options));
        return Server.createWebServer(args.toArray(new String[0])).start();
    }

    private static List<List<String>> resultRows(String page) {
        Matcher table = RESULT_TABLE.matcher(page);
        assertTrue(table.find(), page);
        List<List<String>> rows = new ArrayList<>();
        Matcher row = ROW.matcher(table.group(1));
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1));
            }
            rows.add(cells);
        }
        return rows;
    }

    /**
     * A browser session against the console: the session id the welcome page hands out and the
     * form posts a browser would send with it.
     */
    private static final class Console {

        private static final Pattern SESSION_ID = Pattern.compile("jsessionid=([0-9a-f]+)");

        private final HttpClient http;

        private final String base;

        private final String sessionId;

        private Console(HttpClient http, String base, String sessionId) {
            this.http = http;
            this.base = base;
            this.sessionId = sessionId;
        }

        static Console open(Server server) throws IOException, InterruptedException {
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.getPort() + "/";
            HttpResponse<String> welcome = http.send(HttpRequest.newBuilder(URI.create(base)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, welcome.statusCode());
            assertTrue(welcome.body().contains("<title>H2 Console</title>"));
            Matcher matcher = SESSION_ID.matcher(welcome.body());
            assertTrue(matcher.find(), welcome.body());
            return new Console(http, base, matcher.group(1));
        }

        String get(String page, String... parameters) throws IOException, InterruptedException {
            String query = parameters.length == 0 ? "" : "&" + form(parameters);
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(base + page + "?jsessionid=" + sessionId + query)).build();
            return send(request);
        }

        String post(String page, String... fields) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + page + "?jsessionid=" + sessionId))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                    .build();
            return send(request);
        }

        String query(String sql) throws IOException, InterruptedException {
            return post("query.do", "sql", sql);
        }

        private String send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), request.uri().toString());
            return response.body();
        }

        private static String form(String... pairs) {
            assertEquals(0, pairs.length % 2);
            List<String> encoded = new ArrayList<>();
            for (int i = 0; i < pairs.length; i += 2) {
                encoded.add(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
            }
            return String.join("&", encoded);
        }
    }
}
