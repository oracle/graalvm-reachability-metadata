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
    @Test
    @SuppressWarnings("unchecked")
    void loadCreatesStrategyWithNoArgConstructorAndCachesItByTag() {
        String tag = "topic-compaction-strategy-test";
        TopicCompactionStrategy.INSTANCES.remove(tag);

        TopicCompactionStrategy<String> strategy = (TopicCompactionStrategy<String>) TopicCompactionStrategy.load(
                tag, NewestValueStrategy.class.getName());

        try {
            assertThat(strategy).isInstanceOf(NewestValueStrategy.class);
            assertThat(strategy.getSchema()).isNull();
            assertThat(strategy.shouldKeepLeft("older", "newer")).isFalse();
            assertThat(TopicCompactionStrategy.getInstance(tag)).isSameAs(strategy);
        } finally {
            TopicCompactionStrategy.INSTANCES.remove(tag);
        }
    }

    public static final class NewestValueStrategy implements TopicCompactionStrategy<String> {
        public NewestValueStrategy() {
        }

        @Override
        public Schema<String> getSchema() {
            return null;
        }

        @Override
        public boolean shouldKeepLeft(String prev, String cur) {
            return false;
        }
    }
}
