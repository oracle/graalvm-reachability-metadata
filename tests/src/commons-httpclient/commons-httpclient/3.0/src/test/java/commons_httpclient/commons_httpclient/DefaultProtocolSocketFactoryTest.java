/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.protocol.DefaultProtocolSocketFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DefaultProtocolSocketFactoryTest {
    private static final String DEFAULT_PROTOCOL_SOCKET_FACTORY_CLASS_NAME =
            "org.apache.commons.httpclient.protocol.DefaultProtocolSocketFactory";

    @Test
    void freshFactoryClassComputesHashCodeThroughLegacyClassHelper() throws Exception {
        try (DefaultProtocolSocketFactoryClassLoader classLoader = newDefaultProtocolSocketFactoryClassLoader()) {
            Class<?> factoryClass = Class.forName(
                    DEFAULT_PROTOCOL_SOCKET_FACTORY_CLASS_NAME,
                    true,
                    classLoader);
            Constructor<?> constructor = factoryClass.getConstructor();
            Object factory = constructor.newInstance();

            assertThat(factoryClass.getName()).isEqualTo(DEFAULT_PROTOCOL_SOCKET_FACTORY_CLASS_NAME);
            assertThat(factory.hashCode()).isEqualTo(factoryClass.hashCode());
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void comparesFactoriesByImplementationClass() {
        DefaultProtocolSocketFactory factory = new DefaultProtocolSocketFactory();

        assertThat(factory.hashCode()).isEqualTo(DefaultProtocolSocketFactory.class.hashCode());
        assertThat(factory).isEqualTo(new DefaultProtocolSocketFactory());
        assertThat(factory).isNotEqualTo(new Object());
        assertThat(factory).isNotEqualTo(null);
    }

    private static DefaultProtocolSocketFactoryClassLoader newDefaultProtocolSocketFactoryClassLoader() {
        URL location = DefaultProtocolSocketFactory.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new DefaultProtocolSocketFactoryClassLoader(
                new URL[] {location},
                DefaultProtocolSocketFactoryTest.class.getClassLoader());
    }

    private static final class DefaultProtocolSocketFactoryClassLoader extends URLClassLoader {
        private DefaultProtocolSocketFactoryClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (DEFAULT_PROTOCOL_SOCKET_FACTORY_CLASS_NAME.equals(name)) {
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
