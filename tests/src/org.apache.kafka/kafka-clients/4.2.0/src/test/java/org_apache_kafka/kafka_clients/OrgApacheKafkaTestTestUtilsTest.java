/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaTestTestUtilsTest {

    @Test
    void readsAndWritesPrivateFieldValues() throws Exception {
        FieldBackedValue value = new FieldBackedValue("initial");

        assertThat(TestUtils.<String>fieldValue(value, FieldBackedValue.class, "payload"))
                .isEqualTo("initial");

        TestUtils.setFieldValue(value, "payload", "updated");

        assertThat(value.payload()).isEqualTo("updated");
        assertThat(TestUtils.<String>fieldValue(value, FieldBackedValue.class, "payload"))
                .isEqualTo("updated");
    }

    private static final class FieldBackedValue {
        private String payload;

        private FieldBackedValue(String payload) {
            this.payload = payload;
        }

        private String payload() {
            return payload;
        }
    }
}
