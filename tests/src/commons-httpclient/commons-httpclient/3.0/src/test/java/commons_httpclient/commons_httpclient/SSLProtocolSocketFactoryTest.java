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

import org.apache.commons.httpclient.protocol.SSLProtocolSocketFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class SSLProtocolSocketFactoryTest {
    private static final String SSL_PROTOCOL_SOCKET_FACTORY_CLASS_NAME =
            "org.apache.commons.httpclient.protocol.SSLProtocolSocketFactory";

    @Test
    void freshFactoryClassComputesHashCodeThroughLegacyClassHelper() throws Exception {
        try (SSLProtocolSocketFactoryClassLoader classLoader = newSSLProtocolSocketFactoryClassLoader()) {
            Class<?> factoryClass = classLoader.loadClass(SSL_PROTOCOL_SOCKET_FACTORY_CLASS_NAME);
            Constructor<?> constructor = factoryClass.getConstructor();
            Object factory = constructor.newInstance();

            assertThat(factoryClass.getName()).isEqualTo(SSL_PROTOCOL_SOCKET_FACTORY_CLASS_NAME);
            assertThat(factory.hashCode()).isEqualTo(factoryClass.hashCode());
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void comparesFactoriesByImplementationClass() {
        SSLProtocolSocketFactory factory = new SSLProtocolSocketFactory();

        assertThat(factory.hashCode()).isEqualTo(SSLProtocolSocketFactory.class.hashCode());
        assertThat(factory).isEqualTo(new SSLProtocolSocketFactory());
        assertThat(factory).isNotEqualTo(new Object());
        assertThat(factory).isNotEqualTo(null);
    }

    private static SSLProtocolSocketFactoryClassLoader newSSLProtocolSocketFactoryClassLoader() {
        URL location = SSLProtocolSocketFactory.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new SSLProtocolSocketFactoryClassLoader(
                new URL[] {location},
                SSLProtocolSocketFactoryTest.class.getClassLoader());
    }

    private static final class SSLProtocolSocketFactoryClassLoader extends URLClassLoader {
        private SSLProtocolSocketFactoryClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (SSL_PROTOCOL_SOCKET_FACTORY_CLASS_NAME.equals(name)) {
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
