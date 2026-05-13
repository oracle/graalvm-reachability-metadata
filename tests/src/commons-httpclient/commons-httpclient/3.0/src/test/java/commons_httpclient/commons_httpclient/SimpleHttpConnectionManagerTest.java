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

import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class SimpleHttpConnectionManagerTest {
    private static final String SIMPLE_CONNECTION_MANAGER_CLASS_NAME =
            "org.apache.commons.httpclient.SimpleHttpConnectionManager";

    @Test
    @Order(1)
    void constructorInitializesManagerAndDefaultParameters() {
        SimpleHttpConnectionManager manager = new SimpleHttpConnectionManager();

        assertThat(manager.getParams()).isNotNull();
        assertThat(manager.isConnectionStaleCheckingEnabled())
                .isEqualTo(manager.getParams().isStaleCheckingEnabled());
    }

    @Test
    @Order(2)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (SimpleHttpConnectionManagerClassLoader classLoader =
                newSimpleHttpConnectionManagerClassLoader()) {
            Class<?> connectionManagerClass = Class.forName(
                    SIMPLE_CONNECTION_MANAGER_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(connectionManagerClass.getName())
                    .isEqualTo(SIMPLE_CONNECTION_MANAGER_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(connectionManagerClass).isSameAs(SimpleHttpConnectionManager.class);
            } else {
                assertThat(connectionManagerClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    private static SimpleHttpConnectionManagerClassLoader
            newSimpleHttpConnectionManagerClassLoader() {
        URL location = SimpleHttpConnectionManager.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new SimpleHttpConnectionManagerClassLoader(new URL[] {location},
                SimpleHttpConnectionManagerTest.class.getClassLoader());
    }

    private static final class SimpleHttpConnectionManagerClassLoader extends URLClassLoader {
        private SimpleHttpConnectionManagerClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (SIMPLE_CONNECTION_MANAGER_CLASS_NAME.equals(name)) {
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
