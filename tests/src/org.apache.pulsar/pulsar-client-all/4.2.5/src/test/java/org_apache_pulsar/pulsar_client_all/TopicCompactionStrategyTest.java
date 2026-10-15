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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class TopicCompactionStrategyTest {
    @Test
    void loadsAndCachesConfiguredCompactionStrategy() {
        String tag = "coverage-strategy";
        TopicCompactionStrategy<?> loaded =
                TopicCompactionStrategy.load(tag, KeepLongestStrategy.class.getName());

        assertThat(loaded).isInstanceOf(KeepLongestStrategy.class);
        assertThat(TopicCompactionStrategy.getInstance(tag)).isSameAs(loaded);
        assertThat(((KeepLongestStrategy) loaded).shouldKeepLeft("longer", "short"))
                .isTrue();
        assertThat(loaded.getSchema()).isSameAs(Schema.STRING);
    }

    public static final class KeepLongestStrategy implements TopicCompactionStrategy<String> {
        public KeepLongestStrategy() { }

        @Override
        public Schema<String> getSchema() {
            return Schema.STRING;
        }

        @Override
        public boolean shouldKeepLeft(String previous, String current) {
            return previous.length() >= current.length();
        }
    }
}
