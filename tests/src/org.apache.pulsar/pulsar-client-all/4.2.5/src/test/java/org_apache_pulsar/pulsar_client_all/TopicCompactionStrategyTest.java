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
    void loadsAndCachesCompactionStrategyByClassName() {
        final String tag = "keep-largest-integer";
        try {
            final TopicCompactionStrategy<?> loaded =
                    TopicCompactionStrategy.load(tag, LargestValueStrategy.class.getName());

            assertThat(loaded).isInstanceOf(LargestValueStrategy.class);
            assertThat(TopicCompactionStrategy.getInstance(tag)).isSameAs(loaded);
            assertThat(((LargestValueStrategy) loaded).shouldKeepLeft(8, 3)).isTrue();
            assertThat(((LargestValueStrategy) loaded).shouldKeepLeft(3, 8)).isFalse();
        } finally {
            TopicCompactionStrategy.INSTANCES.remove(tag);
        }
    }

    public static final class LargestValueStrategy implements TopicCompactionStrategy<Integer> {
        public LargestValueStrategy() {
        }

        @Override
        public Schema<Integer> getSchema() {
            return Schema.INT32;
        }

        @Override
        public boolean shouldKeepLeft(Integer previous, Integer current) {
            return previous >= current;
        }
    }
}
