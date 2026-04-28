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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseTest {
    @Test
    void initializesDatabaseEventListenerFromConnectionUrl() throws Exception {
        RecordingDatabaseEventListener.initializedUrl.set(null);
        RecordingDatabaseEventListener.openedCount.set(0);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:database-listener;"
                + "DATABASE_EVENT_LISTENER='" + RecordingDatabaseEventListener.class.getName() + "'");
                ResultSet resultSet = connection.createStatement().executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }

        assertThat(RecordingDatabaseEventListener.initializedUrl.get()).contains("database-listener");
        assertThat(RecordingDatabaseEventListener.openedCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void initializesDatabaseJavaObjectSerializerWhenJavaObjectIsStored() throws Exception {
        RecordingJavaObjectSerializer.serializeCount.set(0);
        RecordingJavaObjectSerializer.deserializeCount.set(0);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:database-serializer;"
                + "JAVA_OBJECT_SERIALIZER='" + RecordingJavaObjectSerializer.class.getName() + "'")) {
            connection.createStatement().execute("CREATE TABLE java_objects (payload JAVA_OBJECT)");
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO java_objects VALUES (?)")) {
                statement.setObject(1, "serializer payload", Types.JAVA_OBJECT);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT payload FROM java_objects")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject(1)).isEqualTo("serializer payload");
            }
        }

        assertThat(RecordingJavaObjectSerializer.serializeCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(RecordingJavaObjectSerializer.deserializeCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void initializesCustomTableEngineWhenCreatingEngineBackedTable() throws Exception {
        DelegatingTableEngine.createTableCount.set(0);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:database-table-engine")) {
            connection.createStatement().execute("CREATE TABLE engine_backed_table (id INTEGER) ENGINE \""
                    + DelegatingTableEngine.class.getName() + "\"");
            assertThat(connection.createStatement().executeUpdate("INSERT INTO engine_backed_table VALUES (42)"))
                    .isEqualTo(1);
            try (ResultSet resultSet = connection.createStatement()
                    .executeQuery("SELECT id FROM engine_backed_table")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(42);
            }
        }

        assertThat(DelegatingTableEngine.createTableCount.get()).isEqualTo(1);
    }

    public static class RecordingDatabaseEventListener implements DatabaseEventListener {
        static final AtomicReference<String> initializedUrl = new AtomicReference<>();
        static final AtomicInteger openedCount = new AtomicInteger();

        @Override
        public void init(String url) {
            initializedUrl.set(url);
        }

        @Override
        public void opened() {
            openedCount.incrementAndGet();
        }
    }

    public static class RecordingJavaObjectSerializer implements JavaObjectSerializer {
        static final AtomicInteger serializeCount = new AtomicInteger();
        static final AtomicInteger deserializeCount = new AtomicInteger();

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

    public static class DelegatingTableEngine implements TableEngine {
        static final AtomicInteger createTableCount = new AtomicInteger();

        @Override
        public Table createTable(CreateTableData data) {
            createTableCount.incrementAndGet();
            return data.session.getDatabase().getStore().createTable(data);
        }
    }
}
