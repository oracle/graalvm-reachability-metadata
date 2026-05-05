/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.api.DatabaseEventListener;
import org.h2.api.JavaObjectSerializer;
import org.h2.tools.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionRemoteTest {
    @BeforeEach
    void resetCounters() {
        RecordingDatabaseEventListener.constructorCount.set(0);
        RecordingJavaObjectSerializer.constructorCount.set(0);
    }

    @Test
    void createsConfiguredDatabaseEventListenerForAutoReconnect() throws SQLException {
        Server server = startTcpServer();
        try {
            String url = remoteUrl(server, "session-remote-event-listener")
                    + ";AUTO_RECONNECT=TRUE;DATABASE_EVENT_LISTENER='"
                    + RecordingDatabaseEventListener.class.getName() + "'";

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                assertThat(connection.isValid(1)).isTrue();
            }
        } finally {
            server.stop();
        }

        assertThat(RecordingDatabaseEventListener.constructorCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void createsConfiguredJavaObjectSerializerFromRemoteSettings() throws SQLException {
        Server server = startTcpServer();
        try {
            String url = remoteUrl(server, "session-remote-java-object-serializer");
            try (Connection bootstrapConnection = DriverManager.getConnection(url, "sa", "");
                    Statement bootstrapStatement = bootstrapConnection.createStatement()) {
                bootstrapStatement.execute("SET JAVA_OBJECT_SERIALIZER '"
                        + RecordingJavaObjectSerializer.class.getName() + "'");
                RecordingJavaObjectSerializer.constructorCount.set(0);

                try (Connection connection = DriverManager.getConnection(url, "sa", "");
                        Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(1);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        } finally {
            server.stop();
        }

        assertThat(RecordingJavaObjectSerializer.constructorCount.get()).isGreaterThanOrEqualTo(1);
    }

    private static Server startTcpServer() throws SQLException {
        return Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
    }

    private static String remoteUrl(Server server, String databaseName) {
        return "jdbc:h2:tcp://127.0.0.1:" + server.getPort() + "/mem:" + databaseName;
    }

    public static class RecordingDatabaseEventListener implements DatabaseEventListener {
        static final AtomicInteger constructorCount = new AtomicInteger();

        public RecordingDatabaseEventListener() {
            constructorCount.incrementAndGet();
        }
    }

    public static class RecordingJavaObjectSerializer implements JavaObjectSerializer {
        static final AtomicInteger constructorCount = new AtomicInteger();

        public RecordingJavaObjectSerializer() {
            constructorCount.incrementAndGet();
        }

        @Override
        public byte[] serialize(Object obj) {
            throw new UnsupportedOperationException("Serialization is not used by this test");
        }

        @Override
        public Object deserialize(byte[] bytes) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }
    }
}
