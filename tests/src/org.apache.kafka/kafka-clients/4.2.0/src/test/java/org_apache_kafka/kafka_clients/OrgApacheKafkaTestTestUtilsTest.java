/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaTestTestUtilsTest {

    @Test
    void testFieldValueAndSetFieldValue() throws Exception {
        TopicPartition topicPartition = new TopicPartition("coverage-topic", 3);

        assertThat(TestUtils.<Integer>fieldValue(topicPartition, TopicPartition.class, "hash")).isZero();

        int computedHash = topicPartition.hashCode();
        assertThat(TestUtils.<Integer>fieldValue(topicPartition, TopicPartition.class, "hash")).isEqualTo(computedHash);

        TestUtils.setFieldValue(topicPartition, "hash", 123456);

        assertThat(TestUtils.<Integer>fieldValue(topicPartition, TopicPartition.class, "hash")).isEqualTo(123456);
        assertThat(topicPartition.hashCode()).isEqualTo(123456);
    }
}
