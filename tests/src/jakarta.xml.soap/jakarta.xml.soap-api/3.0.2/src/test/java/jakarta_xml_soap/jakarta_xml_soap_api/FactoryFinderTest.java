/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_soap.jakarta_xml_soap_api;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.xml.soap.SOAPConnectionFactory;
import jakarta.xml.soap.SOAPException;
import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FactoryFinderTest {
    private static final String SOAP_CONNECTION_FACTORY_PROPERTY = SOAPConnectionFactory.class.getName();
    private static final String INSTANTIABLE_NON_FACTORY_PROVIDER = Object.class.getName();

    @Test
    void osgiServiceLoaderPathLooksUpSoapConnectionFactoryClass() {
        String previousProvider = System.getProperty(SOAP_CONNECTION_FACTORY_PROPERTY);
        System.clearProperty(SOAP_CONNECTION_FACTORY_PROPERTY);
        try {
            assertEquals(
                    "org.glassfish.hk2.osgiresourcelocator.ServiceLoader",
                    ServiceLoader.class.getName());

            SOAPException exception = assertThrows(SOAPException.class, SOAPConnectionFactory::newInstance);

            assertTrue(exception.getMessage().contains("SOAP connection factory"));
        } finally {
            restoreProperty(previousProvider);
        }
    }

    @Test
    void fineLoggingLocatesLoadedProviderResource() {
        Logger logger = Logger.getLogger("jakarta.xml.soap");
        Level previousLevel = logger.getLevel();
        String previousProvider = System.getProperty(SOAP_CONNECTION_FACTORY_PROPERTY);
        logger.setLevel(Level.FINE);
        System.setProperty(SOAP_CONNECTION_FACTORY_PROPERTY, INSTANTIABLE_NON_FACTORY_PROVIDER);
        try {
            SOAPException exception = assertThrows(SOAPException.class, SOAPConnectionFactory::newInstance);

            assertTrue(exception.getMessage().contains(INSTANTIABLE_NON_FACTORY_PROVIDER));
            assertTrue(exception.getMessage().contains(SOAP_CONNECTION_FACTORY_PROPERTY));
        } finally {
            logger.setLevel(previousLevel);
            restoreProperty(previousProvider);
        }
    }

    private static void restoreProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(SOAP_CONNECTION_FACTORY_PROPERTY);
        } else {
            System.setProperty(SOAP_CONNECTION_FACTORY_PROPERTY, previousProvider);
        }
    }
}
