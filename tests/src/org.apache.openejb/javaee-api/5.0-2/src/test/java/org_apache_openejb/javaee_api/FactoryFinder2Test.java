/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.Binding;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.spi.ServiceDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinder2Test {
    private static final String PROVIDER_PROPERTY = Provider.JAXWSPROVIDER_PROPERTY;

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalProviderProperty = System.getProperty(PROVIDER_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalProviderProperty == null) {
            System.clearProperty(PROVIDER_PROPERTY);
        } else {
            System.setProperty(PROVIDER_PROPERTY, originalProviderProperty);
        }
    }

    @Test
    void loadsProviderWithContextClassLoader() {
        currentThread.setContextClassLoader(getClass().getClassLoader());
        System.setProperty(PROVIDER_PROPERTY, ContextClassLoaderProvider.class.getName());

        Provider provider = Provider.provider();

        assertThat(provider).isInstanceOf(ContextClassLoaderProvider.class);
    }

    @Test
    void loadsProviderWithClassForNameWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);
        System.setProperty(PROVIDER_PROPERTY, ClassForNameProvider.class.getName());

        Provider provider = Provider.provider();

        assertThat(provider).isInstanceOf(ClassForNameProvider.class);
    }

    private abstract static class AbstractTestProvider extends Provider {
        @Override
        public ServiceDelegate createServiceDelegate(URL wsdlDocumentLocation, QName serviceName, Class serviceClass) {
            return new NoOpServiceDelegate(wsdlDocumentLocation, serviceName);
        }

        @Override
        public Endpoint createEndpoint(String bindingId, Object implementor) {
            return new NoOpEndpoint(implementor);
        }

        @Override
        public Endpoint createAndPublishEndpoint(String address, Object implementor) {
            return new NoOpEndpoint(implementor);
        }
    }

    public static final class ContextClassLoaderProvider extends AbstractTestProvider {
    }

    public static final class ClassForNameProvider extends AbstractTestProvider {
    }

    private static final class NoOpServiceDelegate extends ServiceDelegate {
        private final URL wsdlDocumentLocation;
        private final QName serviceName;
        private HandlerResolver handlerResolver;
        private Executor executor;

        private NoOpServiceDelegate(URL wsdlDocumentLocation, QName serviceName) {
            this.wsdlDocumentLocation = wsdlDocumentLocation;
            this.serviceName = serviceName;
        }

        @Override
        public <T> T getPort(QName portName, Class<T> serviceEndpointInterface) {
            return null;
        }

        @Override
        public <T> T getPort(Class<T> serviceEndpointInterface) {
            return null;
        }

        @Override
        public void addPort(QName portName, String bindingId, String endpointAddress) {
        }

        @Override
        public <T> Dispatch<T> createDispatch(QName portName, Class<T> type, Service.Mode mode) {
            return null;
        }

        @Override
        public Dispatch<Object> createDispatch(QName portName, JAXBContext context, Service.Mode mode) {
            return null;
        }

        @Override
        public QName getServiceName() {
            return serviceName;
        }

        @Override
        public Iterator<QName> getPorts() {
            return List.<QName>of().iterator();
        }

        @Override
        public URL getWSDLDocumentLocation() {
            return wsdlDocumentLocation;
        }

        @Override
        public HandlerResolver getHandlerResolver() {
            return handlerResolver;
        }

        @Override
        public void setHandlerResolver(HandlerResolver handlerResolver) {
            this.handlerResolver = handlerResolver;
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public void setExecutor(Executor executor) {
            this.executor = executor;
        }
    }

    private static final class NoOpEndpoint extends Endpoint {
        private final Object implementor;
        private List<Source> metadata = List.of();
        private Executor executor;
        private Map<String, Object> properties = Map.of();

        private NoOpEndpoint(Object implementor) {
            this.implementor = implementor;
        }

        @Override
        public Binding getBinding() {
            return null;
        }

        @Override
        public Object getImplementor() {
            return implementor;
        }

        @Override
        public void publish(String address) {
        }

        @Override
        public void publish(Object serverContext) {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public List<Source> getMetadata() {
            return metadata;
        }

        @Override
        public void setMetadata(List<Source> metadata) {
            this.metadata = metadata;
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public void setExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public Map<String, Object> getProperties() {
            return properties;
        }

        @Override
        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }
}
