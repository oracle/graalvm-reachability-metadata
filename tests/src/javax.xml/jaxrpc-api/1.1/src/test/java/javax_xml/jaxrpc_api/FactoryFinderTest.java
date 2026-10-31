/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml.jaxrpc_api;

import javax.xml.rpc.ServiceFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class FactoryFinderTest {
    @Test
    void systemPropertyTakesPrecedenceOverServiceFile() throws Exception {
        assertDiscovery(ServiceFactory.class.getClassLoader(), PropertyServiceFactory.class.getName(),
                PropertyServiceFactory.class);
    }

    @Test
    void systemPropertyWorksWithoutContextClassLoader() throws Exception {
        assertDiscovery(null, PropertyServiceFactory.class.getName(), PropertyServiceFactory.class);
    }

    @Test
    void discoversServiceFileWithContextClassLoader() throws Exception {
        assertDiscovery(ServiceFactory.class.getClassLoader(), null, ServiceFileServiceFactory.class);
    }

    @Test
    void discoversSystemServiceFileWithoutContextClassLoader() throws Exception {
        assertDiscovery(null, null, ServiceFileServiceFactory.class);
    }

    private static void assertDiscovery(ClassLoader loader, String provider,
            Class<? extends ServiceFactory> expectedType) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        String originalProvider = System.getProperty(ServiceFactory.SERVICEFACTORY_PROPERTY);
        try {
            thread.setContextClassLoader(loader);
            setProvider(provider);
            ServiceFactory first = ServiceFactory.newInstance();
            ServiceFactory second = ServiceFactory.newInstance();
            assertInstanceOf(expectedType, first);
            assertInstanceOf(expectedType, second);
            assertNotSame(first, second);
        } finally {
            thread.setContextClassLoader(originalLoader);
            setProvider(originalProvider);
        }
    }

    private static void setProvider(String provider) {
        if (provider == null) {
            System.clearProperty(ServiceFactory.SERVICEFACTORY_PROPERTY);
        } else {
            System.setProperty(ServiceFactory.SERVICEFACTORY_PROPERTY, provider);
        }
    }
}
