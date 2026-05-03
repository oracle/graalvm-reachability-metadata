/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.h2.api.JavaObjectSerializer;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {
    static {
        System.setProperty("h2.javaObjectSerializer", TestJavaObjectSerializer.class.getName());
    }

    @Test
    void usesConfiguredJavaObjectSerializer() {
        byte[] bytes = JdbcUtils.serialize("payload", null);

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("test:payload");
        assertThat(JdbcUtils.deserialize(bytes, null)).isEqualTo("payload");
    }

    @Test
    void opensConnectionWithExplicitDriverClass() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(
                DelegatingDriver.class.getName(), "jdbc:h2:mem:jdbcutils-driver", "sa", "")) {
            assertSingleValue(connection);
        }
    }

    @Test
    void opensConnectionFromExplicitJndiContextClass() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(
                TestJndiContext.class.getName(), "java:h2/jdbcutils-jndi", "sa", "")) {
            assertSingleValue(connection);
        }
    }

    @Test
    void readsInfoMetadataResultSet() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbcutils-metadata")) {
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet resultSet = JdbcUtils.getMetaResultSet(connection, "@info")) {
                boolean foundProductName = false;
                while (resultSet.next()) {
                    if ("meta.getDatabaseProductName".equals(resultSet.getString("KEY"))) {
                        assertThat(resultSet.getString("VALUE")).isEqualTo(metaData.getDatabaseProductName());
                        foundProductName = true;
                    }
                }
                assertThat(foundProductName).isTrue();
            }
        }
    }

    private static void assertSingleValue(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
            assertThat(resultSet.next()).isFalse();
        }
    }

    public static final class TestJavaObjectSerializer implements JavaObjectSerializer {
        public TestJavaObjectSerializer() {
        }

        @Override
        public byte[] serialize(Object obj) {
            return ("test:" + obj).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            String serialized = new String(bytes, StandardCharsets.UTF_8);
            assertThat(serialized).startsWith("test:");
            return serialized.substring("test:".length());
        }
    }

    public static final class DelegatingDriver implements java.sql.Driver {
        public DelegatingDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return new org.h2.Driver().connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:h2:");
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

    public static final class TestJndiContext implements Context {
        public TestJndiContext() {
        }

        @Override
        public Object lookup(String name) {
            assertThat(name).startsWith("java:");
            return new FixedDataSource("jdbc:h2:mem:" + name.substring("java:".length()).replace('/', '-'));
        }

        @Override
        public Object lookup(Name name) throws NamingException {
            return lookup(name.toString());
        }

        @Override
        public void close() {
        }

        @Override
        public String getNameInNamespace() {
            return "";
        }

        @Override
        public Object addToEnvironment(String propName, Object propVal) {
            return null;
        }

        @Override
        public Object removeFromEnvironment(String propName) {
            return null;
        }

        @Override
        public Hashtable<?, ?> getEnvironment() {
            return new java.util.Hashtable<>();
        }

        @Override
        public void bind(Name name, Object obj) throws NamingException {
            throw unsupported();
        }

        @Override
        public void bind(String name, Object obj) throws NamingException {
            throw unsupported();
        }

        @Override
        public void rebind(Name name, Object obj) throws NamingException {
            throw unsupported();
        }

        @Override
        public void rebind(String name, Object obj) throws NamingException {
            throw unsupported();
        }

        @Override
        public void unbind(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public void unbind(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public void rename(Name oldName, Name newName) throws NamingException {
            throw unsupported();
        }

        @Override
        public void rename(String oldName, String newName) throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public void destroySubcontext(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public void destroySubcontext(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Context createSubcontext(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Context createSubcontext(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Object lookupLink(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Object lookupLink(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public NameParser getNameParser(Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public NameParser getNameParser(String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Name composeName(Name name, Name prefix) throws NamingException {
            throw unsupported();
        }

        @Override
        public String composeName(String name, String prefix) {
            return prefix + name;
        }

        private static NamingException unsupported() {
            return new NamingException("This test context only supports lookup");
        }
    }

    private static final class FixedDataSource implements DataSource {
        private final String url;

        private FixedDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
