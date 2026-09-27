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
        EndpointAddress httpAddress =
                EndpointAddress.create("https://example.test:8443/services/greeting?wsdl");

        assertThat(httpAddress.getURI().getScheme()).isEqualTo("https");
        assertThat(httpAddress.getURI().getHost()).isEqualTo("example.test");
        assertThat(httpAddress.getURI().getPort()).isEqualTo(8443);
        assertThat(httpAddress.getURI().getPath()).isEqualTo("/services/greeting");
        assertThat(httpAddress.getURI().getQuery()).isEqualTo("wsdl");
        assertThat(httpAddress.getURL().toExternalForm())
                .isEqualTo("https://example.test:8443/services/greeting?wsdl");

        EndpointAddress customAddress = EndpointAddress.create("jms:queue:greetings");

        assertThat(customAddress.getURI().isOpaque()).isTrue();
        assertThat(customAddress.getURI().getSchemeSpecificPart()).isEqualTo("queue:greetings");
        assertThat(customAddress.getURL()).isNull();
        assertThat(customAddress).hasToString("jms:queue:greetings");
    }
}
