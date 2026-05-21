/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgApacheKafkaCommonUtilsUtilsTest {

    @Test
    void createsKafkaTypeWithParameterizedConstructorByName() throws Exception {
        TopicPartition partition = Utils.newParameterizedInstance(
                TopicPartition.class.getName(),
                String.class,
                "coverage-topic",
                Integer.TYPE,
                2);

        assertThat(partition.topic()).isEqualTo("coverage-topic");
        assertThat(partition.partition()).isEqualTo(2);
    }

    @Test
    void reportsAbstractSerdeAsNotConcrete() {
        assertThatThrownBy(() -> Utils.ensureConcreteSubclass(Serde.class, Serde.class))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("abstract");
    }
}
