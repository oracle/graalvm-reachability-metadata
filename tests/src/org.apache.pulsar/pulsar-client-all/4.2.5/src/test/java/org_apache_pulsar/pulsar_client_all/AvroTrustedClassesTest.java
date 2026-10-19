/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.Test;

public class AvroTrustedClassesTest {
    @Test
    void trustsTheApplicationPojoUsedByAnAvroSchema() {
        Schema<TrustedMessage> schema = Schema.AVRO(TrustedMessage.class);
        TrustedMessage input = new TrustedMessage();
        input.value = "trusted";

        TrustedMessage restored = schema.decode(schema.encode(input));

        assertThat(restored.value).isEqualTo("trusted");
    }

    public static class TrustedMessage {
        public String value;

        public TrustedMessage() {
        }
    }
}
