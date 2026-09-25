/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Hashtable;
import java.util.logging.Logger;

import javax.naming.CompositeName;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;

import org.apache.naming.NamingContext;
import org.apache.naming.ResourceLinkRef;
import org.apache.naming.factory.DataSourceLinkFactory;
import org.apache.naming.factory.ResourceLinkFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataSourceLinkFactoryTest {

    @Test
    void wrapsGlobalDataSourceWithConfiguredCredentials() throws Exception {
        NamingContext global = new NamingContext(
                (Hashtable<String, Object>) (Hashtable<?, ?>) new java.util.Properties(), "global");
        RecordingDataSource dataSource = new RecordingDataSource();
        global.createSubcontext("jdbc");
        global.bind("jdbc/global", dataSource);
        DataSourceLinkFactory.setGlobalContext(global);
        ResourceLinkFactory.registerGlobalResourceAccess(global, "jdbc/local", "jdbc/global");
        ResourceLinkRef reference = new ResourceLinkRef(DataSource.class.getName(), "jdbc/global",
                DataSourceLinkFactory.class.getName(), null);
        reference.add(new StringRefAddr("username", "db-user"));
        reference.add(new StringRefAddr("password", "db-password"));

        try {
            DataSource wrapped = (DataSource) new DataSourceLinkFactory().getObjectInstance(reference,
                    new CompositeName("jdbc/local"), null, null);
            assertThat(wrapped.getConnection()).isNull();
            assertThat(dataSource.username).isEqualTo("db-user");
            assertThat(dataSource.password).isEqualTo("db-password");
            assertThat(wrapped.unwrap(DataSource.class)).isSameAs(dataSource);
        } finally {
            ResourceLinkFactory.deregisterGlobalResourceAccess(global, "jdbc/local");
        }
    }

    public static class RecordingDataSource implements DataSource {
        private String username;
        private String password;

        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            this.username = username;
            this.password = password;
            return null;
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
            throw new SQLException("Unsupported interface: " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
