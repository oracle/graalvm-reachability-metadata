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
    void testFieldValueReadsPrivateField() {
        FieldHolder holder = new FieldHolder("initial");

        String value = TestUtils.fieldValue(holder, FieldHolder.class, "value");

        assertThat(value).isEqualTo("initial");
    }

    @Test
    void testSetFieldValueUpdatesPrivateField() throws Exception {
        FieldHolder holder = new FieldHolder("initial");

        TestUtils.setFieldValue(holder, "value", "updated");
        String value = TestUtils.fieldValue(holder, FieldHolder.class, "value");

        assertThat(value).isEqualTo("updated");
    }

    private static final class FieldHolder {
        private String value;

        FieldHolder(String value) {
            this.value = value;
        }
    }
}
