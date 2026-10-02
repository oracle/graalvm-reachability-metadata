/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_ws.jakarta_xml_ws_api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.xml.ws.Service;
import jakarta.xml.ws.spi.Provider;
import jakarta.xml.ws.spi.ServiceDelegate;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

public class Jakarta_xml_ws_apiServiceLoaderUtilTest {
    @Test
    void createsConfiguredProviderThroughPublicProviderLookup() {
        String propertyName = Provider.class.getName();
        String previousProvider = System.getProperty(propertyName);

        try {
            System.setProperty(propertyName, "com.sun.xml.ws.spi.ProviderImpl");

            Provider provider = Provider.provider();
            QName serviceName = new QName("urn:example", "ConfiguredService");
            ServiceDelegate delegate = provider.createServiceDelegate(null, serviceName, Service.class);

            assertEquals(serviceName, delegate.getServiceName());
        } finally {
            if (previousProvider == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousProvider);
            }
        }
    }
}
