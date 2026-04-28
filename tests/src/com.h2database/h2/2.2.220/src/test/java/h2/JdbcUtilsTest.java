/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.Driver;
import org.h2.api.JavaObjectSerializer;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdbcUtilsTest {
    @Test
    void initializesConfiguredJavaObjectSerializer() {
        assertThat(JdbcUtils.serializer).isInstanceOf(PassThroughJavaObjectSerializer.class);
    }

    @Test
    void loadsUserClassWithDefaultClassLoader() {
        assertThat(JdbcUtils.loadUserClass(Driver.class.getName())).isSameAs(Driver.class);
    }

    @Test
    void createsExplicitJdbcDriverBeforeRejectingUnsuitableUrl() {
        assertThatThrownBy(() -> JdbcUtils.getConnection(Driver.class.getName(), "jdbc:unknown:test", "sa", ""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("not suitable");
    }

    @Test
    void createsJndiContextAndUsesReturnedDataSource() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(EmbeddedDataSourceContext.class.getName(),
                "java:comp/env/jdbc/h2", "sa", "");
                ResultSet resultSet = connection.createStatement().executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void readsConnectionInfoThroughDatabaseMetaDataReflection() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(null, "jdbc:h2:mem:metadata-info;DB_CLOSE_DELAY=-1", "sa", "");
                ResultSet resultSet = JdbcUtils.getMetaResultSet(connection, "@info")) {
            boolean foundProductName = false;
            while (resultSet.next()) {
                if ("meta.getDatabaseProductName".equals(resultSet.getString("KEY"))) {
                    foundProductName = true;
                    assertThat(resultSet.getString("VALUE")).isEqualTo("H2");
                }
            }
            assertThat(foundProductName).isTrue();
        }
    }

    @Test
    void defaultSerializationRoundTripStillUsesJdbcUtils() {
        JavaObjectSerializer configuredSerializer = JdbcUtils.serializer;
        JdbcUtils.serializer = null;
        try {
            byte[] bytes = JdbcUtils.serialize("default serializer", null);
            assertThat(JdbcUtils.deserialize(bytes, null)).isEqualTo("default serializer");
        } finally {
            JdbcUtils.serializer = configuredSerializer;
        }
    }

    public static class EmbeddedDataSourceContext extends InitialContext {
        public EmbeddedDataSourceContext() throws NamingException {
            super(true);
        }

        @Override
        public Object lookup(String name) {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:jndi-context;DB_CLOSE_DELAY=-1");
            return dataSource;
        }
    }

    public static class PassThroughJavaObjectSerializer implements JavaObjectSerializer {
        @Override
        public byte[] serialize(Object obj) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(obj);
            }
            return output.toByteArray();
        }

        @Override
        public Object deserialize(byte[] bytes) throws Exception {
            try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return objectInput.readObject();
            }
        }
    }
}
