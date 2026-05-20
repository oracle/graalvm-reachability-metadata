/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UtilsTest {

    @Test
    void createsParameterizedInstanceFromClassName() throws Exception {
        TopicPartition partition = Utils.newParameterizedInstance(
                TopicPartition.class.getName(),
                String.class,
                "orders",
                Integer.TYPE,
                1);

        assertThat(partition.topic()).isEqualTo("orders");
        assertThat(partition.partition()).isEqualTo(1);
    }

    @Test
    void describesConcreteNestedSubclassesForAbstractClass() {
        assertThatThrownBy(() -> Utils.ensureConcreteSubclass(TopicCollection.class, TopicCollection.class))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining(TopicCollection.TopicNameCollection.class.getName())
                .hasMessageContaining(TopicCollection.TopicIdCollection.class.getName());
    }
}
