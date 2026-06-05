/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.command.CommandScope;
import liquibase.command.core.StartH2CommandStep;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class StartH2CommandStepTest {
    private static final Pattern JSESSION_ID_PATTERN = Pattern.compile("jsessionid=([^&\\\"]+)");
    private static final String ADMIN_PASSWORD = "liquibase-test-admin";

    @Test
    void startH2CommandStartsTcpAndWebConsoles() throws Exception {
        int databasePort = findAvailablePort();
        int webPort = findAvailablePort();
        String previousBindAddress = System.getProperty("h2.bindAddress");
        Path h2Properties = Path.of(System.getProperty("user.home"), ".h2.server.properties");
        byte[] previousH2Properties = Files.exists(h2Properties) ? Files.readAllBytes(h2Properties) : null;
        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        AtomicReference<Throwable> commandFailure = new AtomicReference<>();
        PrintStream originalOut = System.out;
        Thread commandThread = new Thread(() -> executeStartH2Command(databasePort, webPort, commandFailure),
                "liquibase-start-h2-test");

        try (PrintStream capture = new PrintStream(commandOutput, true, StandardCharsets.UTF_8)) {
            writeH2AdminPassword(h2Properties);
            System.setOut(capture);
            commandThread.start();

            waitForOutput(commandOutput, "Connection Information:", Duration.ofSeconds(20));
            assertTcpDatabaseAcceptsConnections(databasePort);

            String output = commandOutput.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("JDBC URL: jdbc:h2:tcp://localhost:" + databasePort + "/mem:dev");
            assertThat(output).contains("Dev Web URL:");
            assertThat(output).contains("Integration Web URL:");
        } finally {
            System.setOut(originalOut);
            shutdownWebServer(webPort);
            shutdownTcpServer(databasePort);
            commandThread.interrupt();
            commandThread.join(Duration.ofSeconds(5).toMillis());
            restoreSystemProperty("h2.bindAddress", previousBindAddress);
            restoreH2Properties(h2Properties, previousH2Properties);
        }

        assertThat(commandThread.isAlive()).isFalse();
        Throwable failure = commandFailure.get();
        if (failure != null) {
            assertThat(rootCause(failure)).isInstanceOf(InterruptedException.class);
        }
    }

    private static void executeStartH2Command(int databasePort, int webPort, AtomicReference<Throwable> commandFailure) {
        try {
            new CommandScope(StartH2CommandStep.COMMAND_NAME)
                    .addArgumentValue(StartH2CommandStep.DB_PORT_ARG, databasePort)
                    .addArgumentValue(StartH2CommandStep.WEB_PORT_ARG, webPort)
                    .addArgumentValue(StartH2CommandStep.BIND_ARG, "127.0.0.1")
                    .addArgumentValue(StartH2CommandStep.USERNAME_ARG, "dbuser")
                    .addArgumentValue(StartH2CommandStep.PASSWORD_ARG, "letmein")
                    .addArgumentValue(StartH2CommandStep.LAUNCH_BROWSER_ARG, true)
                    .execute();
        } catch (Throwable throwable) {
            commandFailure.set(throwable);
        }
    }

    private static void assertTcpDatabaseAcceptsConnections(int databasePort) throws Exception {
        String url = "jdbc:h2:tcp://localhost:" + databasePort + "/mem:dev";
        try (Connection connection = DriverManager.getConnection(url, "dbuser", "letmein")) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    private static void waitForOutput(ByteArrayOutputStream output, String expectedText, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (output.toString(StandardCharsets.UTF_8).contains(expectedText)) {
                return;
            }
            Thread.sleep(100L);
        }
        assertThat(output.toString(StandardCharsets.UTF_8)).contains(expectedText);
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private static void writeH2AdminPassword(Path h2Properties) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(h2Properties)) {
            try (InputStream input = Files.newInputStream(h2Properties)) {
                properties.load(input);
            }
        }
        properties.setProperty("webAdminPassword", ADMIN_PASSWORD);
        try (OutputStream output = Files.newOutputStream(h2Properties)) {
            properties.store(output, "H2 Server Properties for Liquibase StartH2CommandStepTest");
        }
    }

    private static void shutdownWebServer(int webPort) {
        try {
            String adminPage = get("http://localhost:" + webPort + "/admin.do");
            Matcher matcher = JSESSION_ID_PATTERN.matcher(adminPage);
            if (matcher.find()) {
                String sessionId = matcher.group(1);
                get("http://localhost:" + webPort + "/adminLogin.do?jsessionid=" + sessionId
                        + "&password=" + ADMIN_PASSWORD);
                get("http://localhost:" + webPort + "/adminShutdown.do?jsessionid=" + sessionId);
            }
        } catch (Exception ignored) {
            // The command may have failed before the web console was started.
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        try {
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void shutdownTcpServer(int databasePort) {
        try {
            Server.shutdownTcpServer("tcp://localhost:" + databasePort, "", true, true);
        } catch (Exception ignored) {
            // The command may have failed before the TCP server was started.
        }
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static void restoreH2Properties(Path h2Properties, byte[] previousH2Properties) throws IOException {
        if (previousH2Properties == null) {
            Files.deleteIfExists(h2Properties);
        } else {
            Files.write(h2Properties, previousH2Properties);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
