/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.DatabaseEventListener;
import org.h2.api.JavaObjectSerializer;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionRemoteTest {
    @Test
    void opensRemoteSessionWithConfiguredDynamicClasses() throws SQLException {
        RecordingRemoteEventListener.constructedCount.set(0);
        RemoteJavaObjectSerializer.constructedCount.set(0);
        RemoteJavaObjectSerializer.serializeCount.set(0);
        RemoteJavaObjectSerializer.deserializeCount.set(0);

        Server server = Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
        try {
            String databaseName = "sessionRemote" + UUID.randomUUID().toString().replace("-", "");
            String url = "jdbc:h2:tcp://localhost:" + server.getPort() + "/mem:" + databaseName
                    + ";AUTO_RECONNECT=TRUE"
                    + ";DATABASE_EVENT_LISTENER='" + RecordingRemoteEventListener.class.getName() + "'"
                    + ";JAVA_OBJECT_SERIALIZER='" + RemoteJavaObjectSerializer.class.getName() + "'";

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE remote_payloads (payload JAVA_OBJECT)");
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO remote_payloads VALUES (?)")) {
                    statement.setObject(1, "remote-serializer-payload", Types.JAVA_OBJECT);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                try (ResultSet resultSet = connection.createStatement()
                        .executeQuery("SELECT payload, CURRENT_TIMESTAMP FROM remote_payloads")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getObject(1)).isEqualTo("remote-serializer-payload");
                    assertThat(resultSet.getTimestamp(2)).isNotNull();
                    assertThat(resultSet.next()).isFalse();
                }
            }
        } finally {
            server.stop();
        }

        assertThat(RecordingRemoteEventListener.constructedCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(RemoteJavaObjectSerializer.constructedCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(RemoteJavaObjectSerializer.serializeCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(RemoteJavaObjectSerializer.deserializeCount.get()).isGreaterThanOrEqualTo(1);
    }

    public static final class RecordingRemoteEventListener implements DatabaseEventListener {
        private static final AtomicInteger constructedCount = new AtomicInteger();

        public RecordingRemoteEventListener() {
            constructedCount.incrementAndGet();
        }
    }

    public static final class RemoteJavaObjectSerializer implements JavaObjectSerializer {
        private static final AtomicInteger constructedCount = new AtomicInteger();
        private static final AtomicInteger serializeCount = new AtomicInteger();
        private static final AtomicInteger deserializeCount = new AtomicInteger();

        public RemoteJavaObjectSerializer() {
            constructedCount.incrementAndGet();
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
