/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt;

import com.sun.xml.ws.api.EndpointAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class EndpointAddressTest {
    @Test
    void representsNetworkAndCustomEndpointUris() {
        EndpointAddress networkAddress =
                EndpointAddress.create("https://example.test:8443/services/greeting?wsdl");

        assertThat(networkAddress.getURI().getScheme()).isEqualTo("https");
        assertThat(networkAddress.getURI().getHost()).isEqualTo("example.test");
        assertThat(networkAddress.getURI().getPort()).isEqualTo(8443);
        assertThat(networkAddress.getURI().getPath()).isEqualTo("/services/greeting");
        assertThat(networkAddress.getURI().getQuery()).isEqualTo("wsdl");

        EndpointAddress customAddress = EndpointAddress.create("jms:queue:greetings");

        assertThat(customAddress.getURI().isOpaque()).isTrue();
        assertThat(customAddress.getURI().getSchemeSpecificPart()).isEqualTo("queue:greetings");
        assertThat(customAddress.getURL()).isNull();
        assertThat(customAddress).hasToString("jms:queue:greetings");
    }
}
