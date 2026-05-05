/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.DatabaseEventListener;
import org.h2.api.JavaObjectSerializer;
import org.h2.api.TableEngine;
import org.h2.command.ddl.CreateTableData;
import org.h2.table.Table;
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
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseTest {
    @BeforeEach
    void resetTestComponents() {
        RecordingDatabaseEventListener.constructorCalls.set(0);
        RecordingDatabaseEventListener.initializedUrl.set(null);
        RecordingJavaObjectSerializer.constructorCalls.set(0);
        RecordingJavaObjectSerializer.serializeCalls.set(0);
        RecordingJavaObjectSerializer.deserializeCalls.set(0);
        DelegatingTableEngine.constructorCalls.set(0);
        DelegatingTableEngine.createTableCalls.set(0);
    }

    @Test
    void initializesConfiguredDatabaseEventListenerWithDefaultConstructor() throws Exception {
        String databaseName = "database-event-listener";
        String url = "jdbc:h2:mem:" + databaseName + ";DATABASE_EVENT_LISTENER='"
                + RecordingDatabaseEventListener.class.getName() + "'";

        try (Connection connection = DriverManager.getConnection(url)) {
            try (ResultSet resultSet = connection.prepareStatement("SELECT 1").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }

        assertThat(RecordingDatabaseEventListener.constructorCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingDatabaseEventListener.initializedUrl.get()).contains(databaseName);
    }

    @Test
    void initializesConfiguredJavaObjectSerializerWithDefaultConstructor() throws Exception {
        String url = "jdbc:h2:mem:database-java-object-serializer;JAVA_OBJECT_SERIALIZER='"
                + RecordingJavaObjectSerializer.class.getName() + "'";

        try (Connection connection = DriverManager.getConnection(url)) {
            connection.prepareStatement("CREATE TABLE java_objects(payload JAVA_OBJECT)").execute();
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO java_objects VALUES (?)")) {
                statement.setObject(1, "serialized payload", Types.JAVA_OBJECT);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try (ResultSet resultSet = connection.prepareStatement("SELECT payload FROM java_objects").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject(1)).isEqualTo("serialized payload");
            }
        }

        assertThat(RecordingJavaObjectSerializer.constructorCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.serializeCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.deserializeCalls.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void initializesConfiguredTableEngineWithDefaultConstructor() throws Exception {
        String tableEngine = DelegatingTableEngine.class.getName();
        String sql = "CREATE TABLE table_engine_test(id INT PRIMARY KEY, name VARCHAR) ENGINE \"" + tableEngine + "\"";

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:database-table-engine")) {
            connection.prepareStatement(sql).execute();
            assertThat(connection.prepareStatement("INSERT INTO table_engine_test VALUES (1, 'H2')").executeUpdate())
                    .isEqualTo(1);
            try (ResultSet resultSet = connection.prepareStatement("SELECT name FROM table_engine_test WHERE id = 1")
                    .executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("H2");
            }
        }

        assertThat(DelegatingTableEngine.constructorCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(DelegatingTableEngine.createTableCalls.get()).isGreaterThanOrEqualTo(1);
    }

    public static final class RecordingDatabaseEventListener implements DatabaseEventListener {
        private static final AtomicInteger constructorCalls = new AtomicInteger();
        private static final AtomicReference<String> initializedUrl = new AtomicReference<>();

        public RecordingDatabaseEventListener() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void init(String url) {
            initializedUrl.set(url);
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

    public static final class DelegatingTableEngine implements TableEngine {
        private static final AtomicInteger constructorCalls = new AtomicInteger();
        private static final AtomicInteger createTableCalls = new AtomicInteger();

        public DelegatingTableEngine() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public Table createTable(CreateTableData data) {
            createTableCalls.incrementAndGet();
            return data.session.getDatabase().getStore().createTable(data);
        }
    }
}
