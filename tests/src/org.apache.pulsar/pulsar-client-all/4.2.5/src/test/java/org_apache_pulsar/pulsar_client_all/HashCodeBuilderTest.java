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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class HashCodeBuilderTest {
    @Test
    void derivesStableHashFromObjectFields() {
        TopicPosition first = new TopicPosition("orders", 7L);
        TopicPosition same = new TopicPosition("orders", 7L);
        TopicPosition different = new TopicPosition("orders", 8L);

        int firstHash = HashCodeBuilder.reflectionHashCode(first);

        assertThat(HashCodeBuilder.reflectionHashCode(same)).isEqualTo(firstHash);
        assertThat(HashCodeBuilder.reflectionHashCode(different)).isNotEqualTo(firstHash);
    }

    public static final class TopicPosition {
        private final String topic;
        private final long offset;

        TopicPosition(String topic, long offset) {
            this.topic = topic;
            this.offset = offset;
        }
    }
}
