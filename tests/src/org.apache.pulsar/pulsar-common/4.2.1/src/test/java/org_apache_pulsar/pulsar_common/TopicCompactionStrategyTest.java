/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.topics.TopicCompactionStrategy;
import org.junit.jupiter.api.Test;

public class TopicCompactionStrategyTest {
    private static final String STRATEGY_TAG = "custom-topic-compaction-strategy";

    @Test
    void loadsStrategyByClassNameAndCachesInstanceByTag() {
        try {
            final TopicCompactionStrategy<?> loadedStrategy = TopicCompactionStrategy.load(
                    STRATEGY_TAG, KeepLargestIntegerStrategy.class.getName());

            assertThat(loadedStrategy).isInstanceOf(KeepLargestIntegerStrategy.class);
            assertThat(TopicCompactionStrategy.getInstance(STRATEGY_TAG)).isSameAs(loadedStrategy);

            @SuppressWarnings("unchecked")
            final TopicCompactionStrategy<Integer> strategy = (TopicCompactionStrategy<Integer>) loadedStrategy;
            assertThat(strategy.shouldKeepLeft(7, 3)).isTrue();
            assertThat(strategy.shouldKeepLeft(3, 7)).isFalse();
        } finally {
            TopicCompactionStrategy.INSTANCES.remove(STRATEGY_TAG);
        }
    }

    public static final class KeepLargestIntegerStrategy implements TopicCompactionStrategy<Integer> {
        public KeepLargestIntegerStrategy() {
        }

        @Override
        public Schema<Integer> getSchema() {
            return null;
        }

        @Override
        public boolean shouldKeepLeft(Integer prev, Integer cur) {
            return prev >= cur;
        }
    }
}
