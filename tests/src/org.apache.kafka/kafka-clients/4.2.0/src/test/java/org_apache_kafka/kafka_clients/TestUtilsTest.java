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

public class TestUtilsTest {

    @Test
    void readsAndWritesObjectFieldsThroughKafkaTestUtility() throws Exception {
        FieldHolder holder = new FieldHolder("initial");

        assertThat(TestUtils.<String>fieldValue(holder, FieldHolder.class, "value")).isEqualTo("initial");

        TestUtils.setFieldValue(holder, "value", "updated");

        assertThat(holder.value()).isEqualTo("updated");
    }

    private static final class FieldHolder {
        private String value;

        private FieldHolder(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
