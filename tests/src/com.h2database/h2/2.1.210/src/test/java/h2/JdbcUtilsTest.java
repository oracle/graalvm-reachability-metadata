/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest implements Driver {
    @Test
    void loadsUserClassAndConstructsJdbcDriver() throws Exception {
        assertThat(JdbcUtils.loadUserClass("org.h2.Driver")).isSameAs(org.h2.Driver.class);

        try (Connection connection = JdbcUtils.getConnection(getClass().getName(), "jdbc:stub:h2", "sa", "")) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void metadataResultSetReflectivelyInvokesDatabaseMetaDataMethods() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbcUtilsMetadata");
             ResultSet resultSet = JdbcUtils.getMetaResultSet(connection, "@info")) {
            int rows = 0;
            while (resultSet.next()) {
                rows++;
            }
            assertThat(rows).isPositive();
        }
    }

    @Test
    void constructsJndiContextAndLooksUpDataSource() throws Exception {
        try (Connection connection = JdbcUtils.getConnection(TestContext.class.getName(),
                "java:comp/env/jdbc/h2", "sa", "")) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        return DriverManager.getConnection("jdbc:h2:mem:jdbcUtilsDelegate;DB_CLOSE_DELAY=-1", info);
    }

    @Override
    public boolean acceptsURL(String url) {
        return "jdbc:stub:h2".equals(url);
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

    public static final class TestContext implements Context {
        @Override
        public Object lookup(String name) {
            return new TestDataSource();
        }

        @Override
        public Object lookup(Name name) {
            return lookup(name.toString());
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
        public Object lookupLink(Name name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object lookupLink(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NameParser getNameParser(Name name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NameParser getNameParser(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name composeName(Name name, Name prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String composeName(String name, String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object addToEnvironment(String propName, Object propVal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object removeFromEnvironment(String propName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Hashtable<?, ?> getEnvironment() {
            return new Hashtable<>();
        }

        @Override
        public void close() {
        }

        @Override
        public String getNameInNamespace() {
            return "";
        }
    }

    public static final class TestDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection("jdbc:h2:mem:jdbcUtilsJndi;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection("jdbc:h2:mem:jdbcUtilsJndi;DB_CLOSE_DELAY=-1", username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Unsupported unwrap: " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
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
    }
}
