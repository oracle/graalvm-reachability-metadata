/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.api.DatabaseEventListener;
import org.h2.api.JavaObjectSerializer;
import org.h2.api.TableEngine;
import org.h2.command.ddl.CreateTableData;
import org.h2.table.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DatabaseTest {
    @BeforeEach
    void resetCounters() {
        RecordingDatabaseEventListener.initCount.set(0);
        RecordingDatabaseEventListener.lastUrl = null;
        RecordingJavaObjectSerializer.constructorCount.set(0);
        RecordingJavaObjectSerializer.serializeCount.set(0);
        RecordingJavaObjectSerializer.deserializeCount.set(0);
        FailingTableEngine.constructorCount.set(0);
        FailingTableEngine.createTableCount.set(0);
    }

    @Test
    void createsConfiguredDatabaseEventListener() throws SQLException {
        String url = "jdbc:h2:mem:database-event-listener;DATABASE_EVENT_LISTENER='"
                + RecordingDatabaseEventListener.class.getName() + "'";

        try (Connection connection = DriverManager.getConnection(url)) {
            assertThat(connection.isValid(1)).isTrue();
        }

        assertThat(RecordingDatabaseEventListener.initCount).hasValue(1);
        assertThat(RecordingDatabaseEventListener.lastUrl).contains("jdbc:h2:mem:database-event-listener");
    }

    @Test
    void createsConfiguredJavaObjectSerializer() throws SQLException {
        String url = "jdbc:h2:mem:database-java-object-serializer;JAVA_OBJECT_SERIALIZER='"
                + RecordingJavaObjectSerializer.class.getName() + "'";

        try (Connection connection = DriverManager.getConnection(url);
                Statement createTable = connection.createStatement()) {
            createTable.execute("CREATE TABLE objects (payload JAVA_OBJECT)");
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO objects VALUES (?)")) {
                statement.setObject(1, "round trip", Types.JAVA_OBJECT);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try (Statement query = connection.createStatement();
                    ResultSet resultSet = query.executeQuery("SELECT payload FROM objects")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject(1)).isEqualTo("round trip");
                assertThat(resultSet.next()).isFalse();
            }
        }

        assertThat(RecordingJavaObjectSerializer.constructorCount).hasValue(1);
        assertThat(RecordingJavaObjectSerializer.serializeCount).hasPositiveValue();
        assertThat(RecordingJavaObjectSerializer.deserializeCount).hasPositiveValue();
    }

    @Test
    void createsConfiguredTableEngine() throws SQLException {
        String url = "jdbc:h2:mem:database-table-engine";

        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            String sql = """
                    CREATE TABLE custom_engine_table (id INTEGER)
                    ENGINE "%s"
                    """.formatted(FailingTableEngine.class.getName());

            assertThatThrownBy(() -> statement.execute(sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("intentional table engine failure");
        }

        assertThat(FailingTableEngine.constructorCount).hasValue(1);
        assertThat(FailingTableEngine.createTableCount).hasValue(1);
    }

    public static class RecordingDatabaseEventListener implements DatabaseEventListener {
        static final AtomicInteger initCount = new AtomicInteger();
        static volatile String lastUrl;

        @Override
        public void init(String url) {
            initCount.incrementAndGet();
            lastUrl = url;
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
        public byte[] serialize(Object obj) throws Exception {
            serializeCount.incrementAndGet();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(obj);
            }
            return bytes.toByteArray();
        }

        @Override
        public Object deserialize(byte[] bytes) throws Exception {
            deserializeCount.incrementAndGet();
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return input.readObject();
            }
        }
    }

    public static class FailingTableEngine implements TableEngine {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger createTableCount = new AtomicInteger();

        public FailingTableEngine() {
            constructorCount.incrementAndGet();
        }

        @Override
        public Table createTable(CreateTableData data) {
            createTableCount.incrementAndGet();
            throw new IllegalStateException("intentional table engine failure");
        }
    }
}
