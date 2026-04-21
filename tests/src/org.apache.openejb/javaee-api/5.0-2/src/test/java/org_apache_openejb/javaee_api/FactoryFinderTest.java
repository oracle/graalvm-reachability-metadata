/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.net.URL;
import java.rmi.Remote;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.rpc.Call;
import javax.xml.rpc.Service;
import javax.xml.rpc.ServiceException;
import javax.xml.rpc.ServiceFactory;
import javax.xml.rpc.encoding.TypeMappingRegistry;
import javax.xml.rpc.handler.HandlerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalServiceFactoryProperty = System.getProperty(ServiceFactory.SERVICEFACTORY_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalServiceFactoryProperty == null) {
            System.clearProperty(ServiceFactory.SERVICEFACTORY_PROPERTY);
        } else {
            System.setProperty(ServiceFactory.SERVICEFACTORY_PROPERTY, originalServiceFactoryProperty);
        }
    }

    @Test
    void loadsServiceFactoryFromContextClassLoaderServiceDescriptor() throws ServiceException {
        ClassLoader testClassLoader = FactoryFinderTest.class.getClassLoader();
        currentThread.setContextClassLoader(testClassLoader);
        System.clearProperty(ServiceFactory.SERVICEFACTORY_PROPERTY);

        ServiceFactory serviceFactory = ServiceFactory.newInstance();

        assertThat(serviceFactory).isInstanceOf(RecordingServiceFactory.class);
        RecordingServiceFactory recordingServiceFactory = (RecordingServiceFactory) serviceFactory;
        assertThat(recordingServiceFactory.getConstructorContextClassLoader()).isSameAs(testClassLoader);
    }

    @Test
    void loadsServiceFactoryFromSystemServiceDescriptorWhenContextClassLoaderIsNull() throws ServiceException {
        currentThread.setContextClassLoader(null);
        System.clearProperty(ServiceFactory.SERVICEFACTORY_PROPERTY);

        ServiceFactory serviceFactory = ServiceFactory.newInstance();

        assertThat(serviceFactory).isInstanceOf(RecordingServiceFactory.class);
        RecordingServiceFactory recordingServiceFactory = (RecordingServiceFactory) serviceFactory;
        assertThat(recordingServiceFactory.getConstructorContextClassLoader()).isNull();
    }

    public static final class RecordingServiceFactory extends ServiceFactory {
        private final ClassLoader constructorContextClassLoader;

        public RecordingServiceFactory() {
            this.constructorContextClassLoader = Thread.currentThread().getContextClassLoader();
        }

        public ClassLoader getConstructorContextClassLoader() {
            return constructorContextClassLoader;
        }

        @Override
        public Service createService(URL wsdlDocumentLocation, QName serviceName) {
            return new RecordingService(wsdlDocumentLocation, serviceName);
        }

        @Override
        public Service createService(QName serviceName) {
            return new RecordingService(null, serviceName);
        }

        @Override
        public Service loadService(Class serviceEndpointInterface) {
            return new RecordingService(null, new QName(serviceEndpointInterface.getName()));
        }

        @Override
        public Service loadService(URL wsdlDocumentLocation, Class serviceEndpointInterface, Properties properties) {
            return new RecordingService(wsdlDocumentLocation, new QName(serviceEndpointInterface.getName()));
        }

        @Override
        public Service loadService(URL wsdlDocumentLocation, QName serviceName, Properties properties) {
            return new RecordingService(wsdlDocumentLocation, serviceName);
        }
    }

    public static final class RecordingService implements Service {
        private final URL wsdlDocumentLocation;
        private final QName serviceName;

        public RecordingService(URL wsdlDocumentLocation, QName serviceName) {
            this.wsdlDocumentLocation = wsdlDocumentLocation;
            this.serviceName = serviceName;
        }

        @Override
        public Remote getPort(QName portName, Class serviceEndpointInterface) {
            return null;
        }

        @Override
        public Remote getPort(Class serviceEndpointInterface) {
            return null;
        }

        @Override
        public Call[] getCalls(QName portName) {
            return new Call[0];
        }

        @Override
        public Call createCall(QName portName) {
            return null;
        }

        @Override
        public Call createCall(QName portName, QName operationName) {
            return null;
        }

        @Override
        public Call createCall(QName portName, String operationName) {
            return null;
        }

        @Override
        public Call createCall() {
            return null;
        }

        @Override
        public QName getServiceName() {
            return serviceName;
        }

        @Override
        public Iterator getPorts() {
            return Collections.emptyIterator();
        }

        @Override
        public URL getWSDLDocumentLocation() {
            return wsdlDocumentLocation;
        }

        @Override
        public TypeMappingRegistry getTypeMappingRegistry() {
            return null;
        }

        @Override
        public HandlerRegistry getHandlerRegistry() {
            return null;
        }
    }
}
