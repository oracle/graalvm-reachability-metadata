/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MultiThreadedHttpConnectionManagerTest {
    private static final String MULTI_THREADED_CONNECTION_MANAGER_CLASS_NAME =
            "org.apache.commons.httpclient.MultiThreadedHttpConnectionManager";
    private static final String HOST_NAME = "example.invalid";
    private static final int HOST_PORT = 80;

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (MultiThreadedHttpConnectionManagerClassLoader classLoader =
                newMultiThreadedHttpConnectionManagerClassLoader()) {
            Class<?> connectionManagerClass = Class.forName(
                    MULTI_THREADED_CONNECTION_MANAGER_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(connectionManagerClass.getName())
                    .isEqualTo(MULTI_THREADED_CONNECTION_MANAGER_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(connectionManagerClass)
                        .isSameAs(MultiThreadedHttpConnectionManager.class);
            } else {
                assertThat(connectionManagerClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void reusesAndDeletesPooledConnectionsForHostConfiguration() throws Exception {
        MultiThreadedHttpConnectionManager manager = new MultiThreadedHttpConnectionManager();
        try {
            HttpConnectionManagerParams params = manager.getParams();
            params.setDefaultMaxConnectionsPerHost(1);
            params.setMaxTotalConnections(1);

            HostConfiguration hostConfiguration = new HostConfiguration();
            hostConfiguration.setHost(HOST_NAME, HOST_PORT, Protocol.getProtocol("http"));

            HttpConnection connection = manager.getConnectionWithTimeout(hostConfiguration, 1000);

            assertThat(connection.getHost()).isEqualTo(HOST_NAME);
            assertThat(connection.getPort()).isEqualTo(HOST_PORT);
            assertThat(connection.getProtocol().getScheme()).isEqualTo("http");
            assertThat(manager.getConnectionsInPool()).isEqualTo(1);
            assertThat(manager.getConnectionsInPool(hostConfiguration)).isEqualTo(1);

            connection.releaseConnection();
            assertThat(connection.getHost()).isNull();

            HttpConnection reusedConnection = manager.getConnectionWithTimeout(
                    hostConfiguration,
                    1000);
            assertThat(reusedConnection.getHost()).isEqualTo(HOST_NAME);
            assertThat(manager.getConnectionsInPool()).isEqualTo(1);

            reusedConnection.close();
            reusedConnection.releaseConnection();
            manager.deleteClosedConnections();

            assertThat(manager.getConnectionsInPool()).isZero();
        } finally {
            manager.shutdown();
            MultiThreadedHttpConnectionManager.shutdownAll();
        }
    }

    private static MultiThreadedHttpConnectionManagerClassLoader
            newMultiThreadedHttpConnectionManagerClassLoader() {
        URL location = MultiThreadedHttpConnectionManager.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new MultiThreadedHttpConnectionManagerClassLoader(new URL[] {location},
                MultiThreadedHttpConnectionManagerTest.class.getClassLoader());
    }

    private static final class MultiThreadedHttpConnectionManagerClassLoader extends URLClassLoader {
        private MultiThreadedHttpConnectionManagerClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (MULTI_THREADED_CONNECTION_MANAGER_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}
