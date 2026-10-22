/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.admin.JettyAdminServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's HTTP administration subsystem for deployment readiness probes.
/// An operator distinguishes a listening admin endpoint from a writable standalone
/// server, reads its monitoring status, and removes a stopped instance from service.
public class AdminReadinessProbeTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void reportsReadinessOnlyWhileTheServerIsServingRequests() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            int adminPort;
            try (ServerSocket available = new ServerSocket()) {
                available.bind(new InetSocketAddress("127.0.0.1", 0));
                adminPort = available.getLocalPort();
            }
            JettyAdminServer admin = new JettyAdminServer("127.0.0.1", adminPort, 10000,
                    "/commands", JettyAdminServer.DEFAULT_HTTP_VERSION, false, false);
            try {
                admin.setZooKeeperServer(server);
                admin.start();
                URI commands = URI.create("http://127.0.0.1:" + adminPort + "/commands/");
                ObjectMapper json = new ObjectMapper();

                JsonNode starting = request(commands.resolve("is_read_only"), json);
                assertEquals("is_read_only", starting.path("command").asText());
                assertEquals("This ZooKeeper instance is not currently serving requests",
                        starting.path("error").asText(),
                        "HTTP success alone must not admit an unstarted node into service");

                listener.startup(server);
                JsonNode ready = request(commands.resolve("is_read_only"), json);
                assertTrue(ready.path("error").isNull());
                assertTrue(ready.path("read_only").isBoolean());
                assertFalse(ready.path("read_only").asBoolean(), "The node accepts writes");

                JsonNode status = request(commands.resolve("monitor"), json);
                assertTrue(status.path("error").isNull());
                assertEquals("monitor", status.path("command").asText());
                assertEquals("standalone", status.path("server_state").asText(),
                        "The monitoring response identifies the serving role");

                // Drain the client listener while keeping the operator's HTTP endpoint available.
                listener.shutdown();
                JsonNode stopped = request(commands.resolve("is_read_only"), json);
                assertEquals("This ZooKeeper instance is not currently serving requests",
                        stopped.path("error").asText(),
                        "A stopped instance must no longer pass the readiness probe");
                assertTrue(stopped.path("read_only").isMissingNode());
            } finally {
                admin.shutdown();
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static JsonNode request(URI endpoint, ObjectMapper json) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            assertEquals(200, connection.getResponseCode(), "The admin endpoint responds over HTTP");
            assertTrue(connection.getContentType().startsWith("application/json"));
            try (InputStream body = connection.getInputStream()) {
                return json.readTree(body);
            }
        } finally {
            connection.disconnect();
        }
    }
}
