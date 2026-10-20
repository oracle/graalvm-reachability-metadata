/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.topics.TopicCompactionStrategy;
import org.junit.jupiter.api.Test;

public class TopicCompactionStrategyTest {
    @Test
    void loadsAndCachesConfiguredStrategyByClassName() {
        TopicCompactionStrategy<?> loaded = TopicCompactionStrategy.load(
                "coverage", KeepLongestValue.class.getName());

        assertThat(loaded).isInstanceOf(KeepLongestValue.class);
        assertThat(TopicCompactionStrategy.getInstance("coverage")).isSameAs(loaded);
        KeepLongestValue strategy = (KeepLongestValue) loaded;
        assertThat(strategy.getSchema()).isSameAs(Schema.STRING);
        assertThat(strategy.shouldKeepLeft("longer", "new")).isTrue();
        assertThat(strategy.shouldKeepLeft("old", "longer")).isFalse();
    }

    public static class KeepLongestValue implements TopicCompactionStrategy<String> {
        public KeepLongestValue() {
        }

        @Override
        public Schema<String> getSchema() {
            return Schema.STRING;
        }

        @Override
        public boolean shouldKeepLeft(String previous, String current) {
            return previous.length() > current.length();
        }
    }
}
