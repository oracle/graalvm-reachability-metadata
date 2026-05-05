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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionRemoteTest {
    @BeforeAll
    static void configureDefaultJavaObjectSerializerProperty() {
        System.setProperty("h2.javaObjectSerializer", JdbcUtilsTest.TestJavaObjectSerializer.class.getName());
    }

    @BeforeEach
    void resetConfiguredComponents() {
        RecordingDatabaseEventListener.constructorCalls.set(0);
        RecordingJavaObjectSerializer.constructorCalls.set(0);
        RecordingJavaObjectSerializer.serializeCalls.set(0);
        RecordingJavaObjectSerializer.deserializeCalls.set(0);
    }

    @Test
    void remoteSessionInitializesConfiguredClientSideComponents() throws Exception {
        Server server = Server.createTcpServer("-tcpPort", "0", "-tcpDaemon", "-ifNotExists").start();
        try {
            String databaseName = "session-remote-" + UUID.randomUUID();
            String url = "jdbc:h2:tcp://127.0.0.1:" + server.getPort() + "/mem:" + databaseName
                    + ";AUTO_RECONNECT=TRUE"
                    + ";DATABASE_EVENT_LISTENER='" + RecordingDatabaseEventListener.class.getName() + "'";

            try (Connection connection = DriverManager.getConnection(url)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET JAVA_OBJECT_SERIALIZER '"
                            + RecordingJavaObjectSerializer.class.getName() + "'");
                    statement.execute("CREATE TABLE java_objects(payload JAVA_OBJECT)");
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO java_objects VALUES (?)")) {
                    statement.setObject(1, "serialized through remote session", Types.JAVA_OBJECT);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT payload FROM java_objects")) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getObject(1)).isEqualTo("serialized through remote session");
                        assertThat(resultSet.next()).isFalse();
                    }
                }
            }
        } finally {
            server.stop();
        }

        assertThat(RecordingDatabaseEventListener.constructorCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.constructorCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.serializeCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.deserializeCalls.get()).isGreaterThanOrEqualTo(1);
    }

    public static final class RecordingDatabaseEventListener implements DatabaseEventListener {
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        public RecordingDatabaseEventListener() {
            constructorCalls.incrementAndGet();
        }
    }

    public static final class RecordingJavaObjectSerializer implements JavaObjectSerializer {
        private static final AtomicInteger constructorCalls = new AtomicInteger();
        private static final AtomicInteger serializeCalls = new AtomicInteger();
        private static final AtomicInteger deserializeCalls = new AtomicInteger();

        public RecordingJavaObjectSerializer() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public byte[] serialize(Object obj) throws Exception {
            serializeCalls.incrementAndGet();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(obj);
            }
            return output.toByteArray();
        }

        @Override
        public Object deserialize(byte[] bytes) throws Exception {
            deserializeCalls.incrementAndGet();
            try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return objectInput.readObject();
            }
        }
    }
}
