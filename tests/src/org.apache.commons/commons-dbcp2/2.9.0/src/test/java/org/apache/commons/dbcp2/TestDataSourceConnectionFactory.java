/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("resource")
public class TestDataSourceConnectionFactory {

    private static class TestDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            return new TesterConnection(null, null);
        }

        @Override
        public Connection getConnection(final String username, final String password) {
            return new TesterConnection(username, password);
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public Logger getParentLogger() {
            return null;
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }

        @Override
        public void setLoginTimeout(final int seconds) {
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
        }

        @Override
        public <T> T unwrap(final Class<T> iface) {
            return null;
        }
    }

    private DataSource datasource;

    private DataSourceConnectionFactory factory;

    @BeforeEach
    public void setUp() {
        datasource = new TestDataSource();
        factory = new DataSourceConnectionFactory(datasource);
    }

    @Test
    public void testCredentials() throws SQLException {
        final DataSourceConnectionFactory factory = new DataSourceConnectionFactory(datasource, "foo", "bar");
        final Connection conn = factory.createConnection();
        assertEquals("foo", ((TesterConnection) conn).getUserName());
    }

    @Test
    public void testDefaultValues() throws SQLException {
        final Connection conn = factory.createConnection();
        assertNull(((TesterConnection) conn).getUserName());
    }

    @Test
    public void testEmptyPassword() throws SQLException {
        final DataSourceConnectionFactory factory = new DataSourceConnectionFactory(datasource, "foo", (char[]) null);
        final Connection conn = factory.createConnection();
        assertEquals("foo", ((TesterConnection) conn).getUserName());
    }

    @Test
    public void testEmptyUser() throws SQLException {
        final DataSourceConnectionFactory factory = new DataSourceConnectionFactory(datasource, null, new char[]{'a'});
        final Connection conn = factory.createConnection();
        assertNull(((TesterConnection) conn).getUserName());
    }
}
