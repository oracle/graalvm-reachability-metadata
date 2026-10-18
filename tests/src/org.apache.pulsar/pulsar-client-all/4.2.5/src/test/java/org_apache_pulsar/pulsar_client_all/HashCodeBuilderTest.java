/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {

    @Test
    void hashesAllDomainFieldsConsistently() {
        final int first = HashCodeBuilder.reflectionHashCode(new MessageKey("orders", 12));
        final int second = HashCodeBuilder.reflectionHashCode(new MessageKey("orders", 12));
        final int different = HashCodeBuilder.reflectionHashCode(new MessageKey("orders", 13));

        assertThat(first).isEqualTo(second).isNotEqualTo(different);
    }

    public static final class MessageKey {
        private final String topic;
        private final int sequence;

        public MessageKey(String topic, int sequence) {
            this.topic = topic;
            this.sequence = sequence;
        }
    }
}
