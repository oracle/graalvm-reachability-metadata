/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractHttpClientTest {

    @Test
    void createsConnectionManagerFromConfiguredFactoryClassName() {
        TestClientConnectionManagerFactory.invocations = 0;

        BasicHttpParams params = new BasicHttpParams();
        params.setParameter(
                ClientPNames.CONNECTION_MANAGER_FACTORY_CLASS_NAME,
                TestClientConnectionManagerFactory.class.getName());

        DefaultHttpClient httpClient = new DefaultHttpClient(params);
        ClientConnectionManager connectionManager = httpClient.getConnectionManager();
        try {
            assertThat(connectionManager).isInstanceOf(BasicClientConnectionManager.class);
            assertThat(TestClientConnectionManagerFactory.invocations).isEqualTo(1);
        } finally {
            connectionManager.shutdown();
        }
    }

    @Test
    void createsConnectionManagerWhenContextClassLoaderIsNull() {
        TestClientConnectionManagerFactory.invocations = 0;

        BasicHttpParams params = new BasicHttpParams();
        params.setParameter(
                ClientPNames.CONNECTION_MANAGER_FACTORY_CLASS_NAME,
                TestClientConnectionManagerFactory.class.getName());

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(null);

        ClientConnectionManager connectionManager = null;
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient(params);
            connectionManager = httpClient.getConnectionManager();

            assertThat(connectionManager).isInstanceOf(BasicClientConnectionManager.class);
            assertThat(TestClientConnectionManagerFactory.invocations).isEqualTo(1);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
            if (connectionManager != null) {
                connectionManager.shutdown();
            }
        }
    }

    public static class TestClientConnectionManagerFactory implements ClientConnectionManagerFactory {

        private static int invocations;

        @Override
        public ClientConnectionManager newInstance(HttpParams params, SchemeRegistry schemeRegistry) {
            invocations++;
            return new BasicClientConnectionManager(schemeRegistry);
        }
    }
}
