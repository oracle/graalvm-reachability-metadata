/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {
    @Test
    void rendersSelectedFieldsFromAnObject() {
        final String rendered = ReflectionToStringBuilder.toStringInclude(
                new Description("persistent://tenant/ns/topic", 4), "topic", "partitions");

        assertThat(rendered).contains("topic=persistent://tenant/ns/topic", "partitions=4");
        assertThat(ReflectionToStringBuilder.toString(
                        new Description("topic", 1), ToStringStyle.SHORT_PREFIX_STYLE))
                .contains("topic=topic", "partitions=1");
    }

    public static final class Description {
        private final String topic;
        private final int partitions;

        public Description(String topic, int partitions) {
            this.topic = topic;
            this.partitions = partitions;
        }
    }
}
