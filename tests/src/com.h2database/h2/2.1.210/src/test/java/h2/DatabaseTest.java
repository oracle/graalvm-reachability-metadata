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
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseTest {
    @Test
    void opensDatabaseWithConfiguredEventListenerClass() throws SQLException {
        RecordingDatabaseEventListener.constructedCount.set(0);
        RecordingDatabaseEventListener.initializedUrl.set(null);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:databaseEventListener;DATABASE_EVENT_LISTENER="
                + RecordingDatabaseEventListener.class.getName())) {
            connection.prepareStatement("SELECT 1").execute();
        }

        assertThat(RecordingDatabaseEventListener.constructedCount.get()).isEqualTo(1);
        assertThat(RecordingDatabaseEventListener.initializedUrl.get()).contains("jdbc:h2:mem:databaseEventListener");
    }

    @Test
    void serializesJavaObjectWithConfiguredSerializerClass() throws SQLException {
        Utf8JavaObjectSerializer.constructedCount.set(0);
        Utf8JavaObjectSerializer.serializeCount.set(0);
        Utf8JavaObjectSerializer.deserializeCount.set(0);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:databaseJavaObjectSerializer;JAVA_OBJECT_SERIALIZER='"
                + Utf8JavaObjectSerializer.class.getName() + "'")) {
            connection.prepareStatement("CREATE TABLE payloads (payload JAVA_OBJECT)").execute();
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO payloads VALUES (?)")) {
                statement.setObject(1, "database-serializer-payload", Types.JAVA_OBJECT);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try (ResultSet resultSet = connection.prepareStatement("SELECT payload FROM payloads").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject(1)).isEqualTo("database-serializer-payload");
                assertThat(resultSet.next()).isFalse();
            }
        }

        assertThat(Utf8JavaObjectSerializer.constructedCount.get()).isEqualTo(1);
        assertThat(Utf8JavaObjectSerializer.serializeCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(Utf8JavaObjectSerializer.deserializeCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void createsTableWithConfiguredDefaultTableEngineClass() throws SQLException {
        DelegatingTableEngine.constructedCount.set(0);
        DelegatingTableEngine.createTableCount.set(0);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:databaseDefaultTableEngine;DEFAULT_TABLE_ENGINE="
                + DelegatingTableEngine.class.getName())) {
            connection.prepareStatement("CREATE TABLE engine_backed (id INT PRIMARY KEY, name VARCHAR)").execute();
            assertThat(connection.prepareStatement("INSERT INTO engine_backed VALUES (1, 'h2')").executeUpdate()).isEqualTo(1);
            try (ResultSet resultSet = connection.prepareStatement("SELECT name FROM engine_backed WHERE id = 1").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("h2");
            }
        }

        assertThat(DelegatingTableEngine.constructedCount.get()).isEqualTo(1);
        assertThat(DelegatingTableEngine.createTableCount.get()).isGreaterThanOrEqualTo(1);
    }

    public static final class RecordingDatabaseEventListener implements DatabaseEventListener {
        private static final AtomicInteger constructedCount = new AtomicInteger();
        private static final AtomicReference<String> initializedUrl = new AtomicReference<>();

        public RecordingDatabaseEventListener() {
            constructedCount.incrementAndGet();
        }

        @Override
        public void init(String url) {
            initializedUrl.set(url);
        }
    }

    public static final class Utf8JavaObjectSerializer implements JavaObjectSerializer {
        private static final AtomicInteger constructedCount = new AtomicInteger();
        private static final AtomicInteger serializeCount = new AtomicInteger();
        private static final AtomicInteger deserializeCount = new AtomicInteger();

        public Utf8JavaObjectSerializer() {
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

    public static final class DelegatingTableEngine implements TableEngine {
        private static final AtomicInteger constructedCount = new AtomicInteger();
        private static final AtomicInteger createTableCount = new AtomicInteger();

        public DelegatingTableEngine() {
            constructedCount.incrementAndGet();
        }

        @Override
        public Table createTable(CreateTableData data) {
            createTableCount.incrementAndGet();
            return data.session.getDatabase().getStore().createTable(data);
        }
    }
}
