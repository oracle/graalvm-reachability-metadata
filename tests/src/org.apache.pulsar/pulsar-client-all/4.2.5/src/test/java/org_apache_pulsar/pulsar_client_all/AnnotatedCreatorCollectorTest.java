/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class AnnotatedCreatorCollectorTest {

    @Test
    void discoversAndInvokesAStaticJsonCreator() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final TopicName topic = mapper.readValue("\"persistent://public/default/orders\"", TopicName.class);

        assertThat(topic.value).isEqualTo("persistent://public/default/orders");
        assertThat(mapper.writeValueAsString(topic)).isEqualTo("\"persistent://public/default/orders\"");
    }

    public static final class TopicName {
        private final String value;

        private TopicName(String value) {
            this.value = value;
        }

        @JsonCreator
        public static TopicName parse(String value) {
            return new TopicName(value);
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
