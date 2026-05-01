/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void deserializesMapKeysWithStringFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TraceCounters counters = mapper.readValue(
                """
                {
                  "spansByTopic": {
                    "ingest": 13,
                    "query": 21
                  }
                }
                """,
                TraceCounters.class);

        assertThat(counters.spansByTopic)
                .containsEntry(TraceTopicKey.valueOf("ingest"), 13)
                .containsEntry(TraceTopicKey.valueOf("query"), 21);
    }

    public static class TraceCounters {
        public Map<TraceTopicKey, Integer> spansByTopic;
    }

    public static final class TraceTopicKey {
        private final String topic;

        private TraceTopicKey(CharSequence topic) {
            this.topic = topic.toString();
        }

        public static TraceTopicKey valueOf(String topic) {
            return new TraceTopicKey("trace-" + topic);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TraceTopicKey)) {
                return false;
            }
            TraceTopicKey that = (TraceTopicKey) other;
            return Objects.equals(topic, that.topic);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic);
        }
    }
}
