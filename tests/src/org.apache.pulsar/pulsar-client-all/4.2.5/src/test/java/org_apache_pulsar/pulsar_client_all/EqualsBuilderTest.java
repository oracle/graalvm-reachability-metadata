/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    void comparesAllDomainFields() {
        assertThat(EqualsBuilder.reflectionEquals(new Topic("events", 4), new Topic("events", 4))).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(new Topic("events", 4), new Topic("events", 5))).isFalse();
    }

    public static final class Topic {
        private final String name;
        private final int partitions;

        public Topic(String name, int partitions) {
            this.name = name;
            this.partitions = partitions;
        }
    }
}
