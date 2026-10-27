/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt;

import jakarta.xml.ws.Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.namespace.QName;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class ProviderImplTest {
    @Test
    void createsServiceThroughJaxWsProvider() {
        QName serviceName = new QName("urn:example:greetings", "GreetingService");

        Service service = Service.create(serviceName);

        assertThat(service.getServiceName()).isEqualTo(serviceName);
        assertThat(service.getWSDLDocumentLocation()).isNull();
        assertThat(service.getPorts().hasNext()).isFalse();
    }
}
