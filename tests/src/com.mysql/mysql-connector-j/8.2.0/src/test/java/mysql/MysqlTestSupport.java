/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.PooledConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MysqlTestSupport implements BeforeAllCallback {

    private static String endpoint;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.create(MysqlTestSupport.class));
        if (store.get("server") == null) {
            Server server = new Server();
            store.put("server", server);
            try {
                server.start();
            } catch (Exception e) {
                server.close();
                throw e;
            }
        }
    }

    static String host() {
        return endpoint;
    }

    static String replicaHost() {
        return endpoint.replace("127.0.0.1", "localhost");
    }

    static String url(String mode) {
        String hosts = mode.isEmpty() ? host() : host() + "," + replicaHost();
        String protocol = mode.equals("failover") ? "" : mode;
        return "jdbc:mysql:" + protocol + "//" + hosts + "/test";
    }

    static Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user", "fred");
        properties.setProperty("password", "secret");
        properties.setProperty("sslMode", "DISABLED");
        properties.setProperty("allowPublicKeyRetrieval", "true");
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "10000");
        properties.setProperty("retriesAllDown", "1");
        return properties;
    }

    static Connection connect(String mode, Map<String, String> extra) throws SQLException {
        Properties properties = properties();
        properties.putAll(extra);
        return DriverManager.getConnection(url(mode), properties);
    }

    static PooledConnection pooledConnection() throws SQLException {
        MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
        dataSource.setURL(url("") + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=10000");
        dataSource.setUser("fred");
        dataSource.setPassword("secret");
        return dataSource.getPooledConnection();
    }

    static void assertQuery(Connection connection, int expected) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT " + expected)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(expected);
            assertThat(result.next()).isFalse();
        }
    }

    private static String docker(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "docker";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new IOException("Docker command timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IOException("Docker command failed: " + output);
            }
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static final class Server implements ExtensionContext.Store.CloseableResource {
        private String container;

        void start() throws IOException, InterruptedException {
            container = docker("run", "--detach", "--rm", "--publish", "127.0.0.1::3306",
                    "--env", "MYSQL_ROOT_PASSWORD=secret", "--env", "MYSQL_DATABASE=test",
                    "--env", "MYSQL_USER=fred", "--env", "MYSQL_PASSWORD=secret",
                    "container-registry.oracle.com/mysql/community-server:26.7.1");
            endpoint = docker("port", container, "3306/tcp").lines().findFirst().orElseThrow();
            Awaitility.await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                    .ignoreExceptionsMatching(e -> e instanceof SQLException).until(() -> {
                        try (Connection connection = connect("", Map.of())) {
                            return connection.isValid(10);
                        }
                    });
        }

        @Override
        public void close() throws IOException, InterruptedException {
            if (container != null) {
                docker("rm", "--force", container);
                container = null;
            }
        }
    }
}
