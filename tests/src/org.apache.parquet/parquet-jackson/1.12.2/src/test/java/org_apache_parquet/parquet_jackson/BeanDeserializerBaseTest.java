/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class BeanDeserializerBaseTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesNonStaticInnerClassValuedProperty() throws Exception {
        Envelope envelope = MAPPER.readValue("""
                {
                  "name": "outer",
                  "detail": {
                    "value": "inner",
                    "quantity": 7
                  }
                }
                """, Envelope.class);

        assertThat(envelope.name).isEqualTo("outer");
        assertThat(envelope.detail).isNotNull();
        assertThat(envelope.detail.value).isEqualTo("inner");
        assertThat(envelope.detail.quantity).isEqualTo(7);
        assertThat(envelope.detail.ownerName()).isEqualTo("outer");
    }

    public static final class Envelope {
        public String name;
        public Detail detail;

        public class Detail {
            public String value;
            public int quantity;

            public String ownerName() {
                return name;
            }
        }
    }
}
