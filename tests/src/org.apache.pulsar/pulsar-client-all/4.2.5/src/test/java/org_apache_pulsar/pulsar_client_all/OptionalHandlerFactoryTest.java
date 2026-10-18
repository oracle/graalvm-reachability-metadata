/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.namespace.QName;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class OptionalHandlerFactoryTest {

    @Test
    void roundTripsAnXmlQualifiedNameWithJacksonsOptionalHandler() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final QName source = new QName("urn:pulsar:test", "orders");

        final String json = mapper.writeValueAsString(source);
        final QName restored = mapper.readValue(json, QName.class);

        assertThat(restored).isEqualTo(source);
        assertThat(json).contains("orders");
    }
}
