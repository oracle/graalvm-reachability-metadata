/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class CompareToBuilderTest {
    @Test
    void comparesObjectFieldsInDeclarationOrder() {
        TopicPosition earlier = new TopicPosition("orders", 4L);
        TopicPosition later = new TopicPosition("orders", 9L);

        int comparison = CompareToBuilder.reflectionCompare(earlier, later);

        assertThat(comparison).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(later, earlier)).isPositive();
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
