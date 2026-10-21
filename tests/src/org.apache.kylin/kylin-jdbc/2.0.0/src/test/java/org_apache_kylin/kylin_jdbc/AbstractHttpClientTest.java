/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.http.conn.ClientConnectionManager;
import org.apache.kylin.jdbc.shaded.org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.kylin.jdbc.shaded.org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.kylin.jdbc.shaded.org.apache.http.impl.client.DefaultHttpClient;
import org.apache.kylin.jdbc.shaded.org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.kylin.jdbc.shaded.org.apache.http.params.HttpParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class AbstractHttpClientTest {
    @Test
    void createsTheConfiguredConnectionManagerFactory() {
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter("http.connection-manager.factory-class-name", ManagerFactory.class.getName());

        ClientConnectionManager manager = client.getConnectionManager();
        try {
            assertThat(manager).isInstanceOf(BasicClientConnectionManager.class);
        } finally {
            manager.shutdown();
        }
    }

    public static class ManagerFactory implements ClientConnectionManagerFactory {
        public ManagerFactory() {
        }

        @Override
        public ClientConnectionManager newInstance(HttpParams params, SchemeRegistry schemeRegistry) {
            return new BasicClientConnectionManager(schemeRegistry);
        }
    }
}
