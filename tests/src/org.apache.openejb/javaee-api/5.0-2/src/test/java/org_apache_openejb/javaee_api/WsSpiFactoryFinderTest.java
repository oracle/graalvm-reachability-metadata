/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.spi.ServiceDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WsSpiFactoryFinderTest {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalProviderProperty = System.getProperty(Provider.JAXWSPROVIDER_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalProviderProperty == null) {
            System.clearProperty(Provider.JAXWSPROVIDER_PROPERTY);
        } else {
            System.setProperty(Provider.JAXWSPROVIDER_PROPERTY, originalProviderProperty);
        }
    }

    @Test
    void loadsProviderFromSystemPropertyUsingContextClassLoader() {
        ClassLoader testClassLoader = WsSpiFactoryFinderTest.class.getClassLoader();
        currentThread.setContextClassLoader(testClassLoader);
        System.setProperty(Provider.JAXWSPROVIDER_PROPERTY, RecordingProvider.class.getName());

        Provider provider = Provider.provider();

        assertThat(provider).isInstanceOf(RecordingProvider.class);
        RecordingProvider recordingProvider = (RecordingProvider) provider;
        assertThat(recordingProvider.getConstructorContextClassLoader()).isSameAs(testClassLoader);
    }

    @Test
    void loadsProviderFromSystemPropertyUsingClassForNameWhenContextClassLoaderCannotResolveProvider() {
        ClassLoader isolatedClassLoader = new ClassLoader(null) {
        };
        currentThread.setContextClassLoader(isolatedClassLoader);
        System.setProperty(Provider.JAXWSPROVIDER_PROPERTY, RecordingProvider.class.getName());

        Provider provider = Provider.provider();

        assertThat(provider).isInstanceOf(RecordingProvider.class);
        RecordingProvider recordingProvider = (RecordingProvider) provider;
        assertThat(recordingProvider.getConstructorContextClassLoader()).isSameAs(isolatedClassLoader);
    }

    public static final class RecordingProvider extends Provider {
        private final ClassLoader constructorContextClassLoader;

        public RecordingProvider() {
            this.constructorContextClassLoader = Thread.currentThread().getContextClassLoader();
        }

        public ClassLoader getConstructorContextClassLoader() {
            return constructorContextClassLoader;
        }

        @Override
        public ServiceDelegate createServiceDelegate(URL wsdlDocumentLocation, QName serviceName, Class serviceClass) {
            return null;
        }

        @Override
        public Endpoint createEndpoint(String bindingId, Object implementor) {
            return null;
        }

        @Override
        public Endpoint createAndPublishEndpoint(String address, Object implementor) {
            return null;
        }
    }
}
