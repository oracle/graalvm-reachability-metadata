/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ObjectArrayDeserializerTest {
    @Test
    void wrapsSingleJsonValueInTypedArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        String[] values = mapper.readValue("\"topic-a\"", String[].class);

        assertThat(values).containsExactly("topic-a");
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
    }
}
