/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.JavaObjectSerializer;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Hashtable;
import java.util.logging.Logger;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest implements Context, DataSource, JavaObjectSerializer {
    private static final String JNDI_NAME = "java:comp/env/jdbc/h2";
    private static final String JNDI_URL = "jdbc:h2:mem:jdbcUtilsJndi;DB_CLOSE_DELAY=-1";

    private final Hashtable<String, Object> environment = new java.util.Hashtable<>();

    @Test
    void usesConfiguredDriverClassToOpenConnection() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(
                "org.h2.Driver", "jdbc:h2:mem:jdbcUtilsDriver;DB_CLOSE_DELAY=-1", "sa", "")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }

    @Test
    void usesConfiguredJndiContextClassToOpenConnection() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(JdbcUtilsTest.class.getName(), JNDI_NAME, null, null)) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }

    @Test
    void readsDatabaseMetadataThroughMetaResultSet() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbcUtilsMeta;DB_CLOSE_DELAY=-1");
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
    void serializesAndDeserializesJavaObjectsWithDefaultSerializer() {
        JavaObjectSerializer configuredSerializer = JdbcUtils.serializer;
        JdbcUtils.serializer = null;
        try {
            byte[] serialized = JdbcUtils.serialize("jdbc-utils-payload", null);

            assertThat(JdbcUtils.deserialize(serialized, null)).isEqualTo("jdbc-utils-payload");
        } finally {
            JdbcUtils.serializer = configuredSerializer;
        }
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        return lookup(name.toString());
    }

    @Override
    public Object lookup(String name) throws NamingException {
        if (JNDI_NAME.equals(name)) {
            return this;
        }
        throw new NameNotFoundException(name);
    }

    @Override
    public void bind(Name name, Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void bind(String name, Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rebind(Name name, Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rebind(String name, Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unbind(Name name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unbind(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rename(Name oldName, Name newName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rename(String oldName, String newName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void destroySubcontext(Name name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void destroySubcontext(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Context createSubcontext(Name name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Context createSubcontext(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return lookup(name);
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return lookup(name);
    }

    @Override
    public NameParser getNameParser(Name name) {
        return CompositeName::new;
    }

    @Override
    public NameParser getNameParser(String name) {
        return CompositeName::new;
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        Name result = (Name) prefix.clone();
        result.addAll(name);
        return result;
    }

    @Override
    public String composeName(String name, String prefix) {
        return prefix + '/' + name;
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) {
        return environment.put(propName, propVal);
    }

    @Override
    public Object removeFromEnvironment(String propName) {
        return environment.remove(propName);
    }

    @Override
    public Hashtable<?, ?> getEnvironment() {
        return environment;
    }

    @Override
    public void close() {
    }

    @Override
    public String getNameInNamespace() {
        return "java:comp/env";
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JNDI_URL);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return DriverManager.getConnection(JNDI_URL, username, password);
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
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    @Override
    public byte[] serialize(Object obj) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(buffer)) {
            outputStream.writeObject(obj);
        }
        return buffer.toByteArray();
    }

    @Override
    public Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return inputStream.readObject();
        }
    }
}
