/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.net.URL;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.rpc.Service;
import javax.xml.rpc.ServiceException;
import javax.xml.rpc.ServiceFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private static final String SERVICE_FACTORY_PROPERTY = ServiceFactory.class.getName();

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalServiceFactoryProperty = System.getProperty(SERVICE_FACTORY_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalServiceFactoryProperty == null) {
            System.clearProperty(SERVICE_FACTORY_PROPERTY);
        } else {
            System.setProperty(SERVICE_FACTORY_PROPERTY, originalServiceFactoryProperty);
        }
    }

    @Test
    void loadsProviderFromSystemResourcesWhenContextClassLoaderIsNull() throws Exception {
        currentThread.setContextClassLoader(null);
        System.clearProperty(SERVICE_FACTORY_PROPERTY);

        ServiceFactory serviceFactory = ServiceFactory.newInstance();

        assertThat(serviceFactory).isInstanceOf(ServiceResourceServiceFactory.class);
    }

    @Test
    void loadsProviderFromContextClassLoaderWhenAvailable() throws Exception {
        currentThread.setContextClassLoader(FactoryFinderTest.class.getClassLoader());
        System.clearProperty(SERVICE_FACTORY_PROPERTY);

        ServiceFactory serviceFactory = ServiceFactory.newInstance();

        assertThat(serviceFactory).isInstanceOf(ServiceResourceServiceFactory.class);
    }

    public static final class ServiceResourceServiceFactory extends ServiceFactory {
        @Override
        public Service createService(URL wsdlDocumentLocation, QName serviceName) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public Service createService(QName serviceName) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public Service loadService(Class serviceInterface) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public Service loadService(URL wsdlDocumentLocation, Class serviceInterface, Properties properties) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public Service loadService(URL wsdlDocumentLocation, QName serviceName, Properties properties)
                throws ServiceException {
            throw new UnsupportedOperationException("Not used in tests");
        }
    }
}
