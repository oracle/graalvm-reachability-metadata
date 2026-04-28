/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.DatabaseEventListener;
import org.h2.api.JavaObjectSerializer;
import org.h2.jdbc.JdbcConnection;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionRemoteTest {
    private static final AtomicInteger DATABASE_ID = new AtomicInteger();

    @Test
    void createsConfiguredDatabaseEventListenerForRemoteAutoReconnectSession() throws Exception {
        RecordingDatabaseEventListener.constructorCount.set(0);
        Server server = startTcpServer();
        try {
            String url = remoteDatabaseUrl(server, "session_remote_listener")
                    + ";AUTO_RECONNECT=TRUE;DATABASE_EVENT_LISTENER='"
                    + RecordingDatabaseEventListener.class.getName() + "'";

            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    ResultSet resultSet = connection.createStatement().executeQuery("SELECT 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        } finally {
            server.stop();
        }

        assertThat(RecordingDatabaseEventListener.constructorCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void createsConfiguredJavaObjectSerializerFromRemoteSessionSettings() throws Exception {
        RecordingJavaObjectSerializer.constructorCount.set(0);
        RecordingJavaObjectSerializer.serializeCount.set(0);
        RecordingJavaObjectSerializer.deserializeCount.set(0);
        Server server = startTcpServer();
        try {
            String url = remoteDatabaseUrl(server, "session_remote_serializer")
                    + ";JAVA_OBJECT_SERIALIZER='" + RecordingJavaObjectSerializer.class.getName() + "'";

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                JavaObjectSerializer serializer = ((JdbcConnection) connection).getJavaObjectSerializer();
                assertThat(serializer).isInstanceOf(RecordingJavaObjectSerializer.class);

                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE java_objects (payload JAVA_OBJECT)");
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO java_objects VALUES (?)")) {
                    statement.setObject(1, "remote serializer payload", Types.JAVA_OBJECT);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                try (Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("SELECT payload FROM java_objects")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getObject(1)).isEqualTo("remote serializer payload");
                }
            }
        } finally {
            server.stop();
        }

        assertThat(RecordingJavaObjectSerializer.constructorCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.serializeCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.deserializeCount.get()).isGreaterThanOrEqualTo(1);
    }

    private static Server startTcpServer() throws Exception {
        return Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
    }

    private static String remoteDatabaseUrl(Server server, String prefix) {
        return "jdbc:h2:" + server.getURL() + "/mem:" + prefix + '_' + DATABASE_ID.incrementAndGet();
    }

    public static class RecordingDatabaseEventListener implements DatabaseEventListener {
        static final AtomicInteger constructorCount = new AtomicInteger();

        public RecordingDatabaseEventListener() {
            constructorCount.incrementAndGet();
        }
    }

    public static class RecordingJavaObjectSerializer implements JavaObjectSerializer {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger serializeCount = new AtomicInteger();
        static final AtomicInteger deserializeCount = new AtomicInteger();

        public RecordingJavaObjectSerializer() {
            constructorCount.incrementAndGet();
        }

        @Override
        public byte[] serialize(Object obj) {
            serializeCount.incrementAndGet();
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            deserializeCount.incrementAndGet();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
