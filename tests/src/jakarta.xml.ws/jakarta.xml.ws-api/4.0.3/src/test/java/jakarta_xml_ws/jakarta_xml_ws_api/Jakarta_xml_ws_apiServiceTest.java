/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_ws.jakarta_xml_ws_api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.xml.namespace.QName;
import jakarta.xml.ws.Service;
import org.junit.jupiter.api.Test;

public class Jakarta_xml_ws_apiServiceTest {
    @Test
    void createsServiceWithQualifiedName() {
        QName serviceName = new QName("urn:example", "ExampleService");

        Service service = Service.create(serviceName);

        assertEquals(serviceName, service.getServiceName());
    }
}
