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
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;

import org.h2.Driver;
import org.h2.api.JavaObjectSerializer;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {
    private static final String JNDI_URL = "java:comp/env/jdbc/h2-jdbc-utils";

    @AfterEach
    void resetSerializer() {
        JdbcUtils.serializer = null;
    }

    @Test
    void loadsUserClassWithDefaultClassLookup() {
        Class<String> type = JdbcUtils.loadUserClass(String.class.getName());

        assertThat(type).isSameAs(String.class);
    }

    @Test
    void opensConnectionWithExplicitJdbcDriver() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(
                DelegatingDriver.class.getName(), "jdbc:jdbc-utils:delegating-driver", "sa", "")) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void opensConnectionWithJndiContextClass() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(SimpleContext.class.getName(), JNDI_URL, "sa", "")) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void readsConnectionAndDatabaseMetadata() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(
                "org.h2.Driver", "jdbc:h2:mem:jdbc-utils-metadata", "sa", "");
                ResultSet resultSet = JdbcUtils.getMetaResultSet(connection, "@info")) {
            boolean productNameFound = false;
            while (resultSet.next()) {
                if ("meta.getDatabaseProductName".equals(resultSet.getString(1))) {
                    productNameFound = true;
                    assertThat(resultSet.getString(2)).contains("H2");
                }
            }
            assertThat(productNameFound).isTrue();
        }
    }

    @Test
    void serializesWithDefaultJavaSerializationWhenCustomSerializerIsCleared() throws Exception {
        JdbcUtils.serializer = null;

        byte[] serialized = JdbcUtils.serialize("round-trip", null);
        Object deserialized = JdbcUtils.deserialize(serialized, null);

        assertThat(deserialized).isEqualTo("round-trip");
    }

    public static class CountingJavaObjectSerializer implements JavaObjectSerializer {
        @Override
        public byte[] serialize(Object obj) throws Exception {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(obj);
            }
            return bytes.toByteArray();
        }

        @Override
        public Object deserialize(byte[] bytes) throws Exception {
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return input.readObject();
            }
        }
    }

    public static class DelegatingDriver implements java.sql.Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            return new Driver().connect("jdbc:h2:mem:jdbc-utils-delegating-driver", info);
        }

        @Override
        public boolean acceptsURL(String url) {
            return "jdbc:jdbc-utils:delegating-driver".equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    public static class SimpleContext extends InitialContext {
        public SimpleContext() throws NamingException {
            super(false);
        }

        @Override
        public Object lookup(String name) throws NamingException {
            if (!JNDI_URL.equals(name)) {
                throw new NamingException("Unexpected JNDI name: " + name);
            }
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:jdbc-utils-jndi");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Override
        public Object lookup(Name name) throws NamingException {
            return lookup(name.toString());
        }
    }
}
