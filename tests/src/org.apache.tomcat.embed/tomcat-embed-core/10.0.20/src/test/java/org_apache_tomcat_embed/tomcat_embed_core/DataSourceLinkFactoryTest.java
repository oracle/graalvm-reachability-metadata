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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

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
    void suppliesConfiguredCredentialsThroughDataSourceProxy() throws Exception {
        RecordingDataSource target = new RecordingDataSource();
        NamingContext globalContext = new NamingContext((Hashtable<String, Object>) (Hashtable<?, ?>) new java.util.Properties(), "global-data-source");
        globalContext.bind("sharedDataSource", target);
        DataSourceLinkFactory.setGlobalContext(globalContext);
        ResourceLinkFactory.registerGlobalResourceAccess(globalContext, "localDataSource", "sharedDataSource");
        ResourceLinkRef reference = new ResourceLinkRef(DataSource.class.getName(), "sharedDataSource", null, null);
        reference.add(new StringRefAddr("username", "tomcat"));
        reference.add(new StringRefAddr("password", "secret"));

        try {
            DataSource dataSource = (DataSource) new DataSourceLinkFactory()
                    .getObjectInstance(reference, null, null, null);
            assertThat(dataSource).isNotSameAs(target);
            assertThat(dataSource.unwrap(DataSource.class)).isSameAs(target);
            assertThat(target.credentials().get()).isNull();
        } finally {
            ResourceLinkFactory.deregisterGlobalResourceAccess(globalContext, "localDataSource");
        }
    }

    public static final class RecordingDataSource implements DataSource {

        private final AtomicReference<String> credentials = new AtomicReference<>();

        AtomicReference<String> credentials() {
            return credentials;
        }

        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            credentials.set(username + ":" + password);
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
