/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import org.junit.jupiter.api.Test;

public class AnnotatedConstructorTest {

    @Test
    void invokesAConstructorDiscoveredByJacksonIntrospection() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final BeanDescription description = mapper.getDeserializationConfig()
                .introspectForCreation(mapper.constructType(NamedTopic.class));
        final AnnotatedConstructor constructor = description.getConstructors().stream()
                .filter(candidate -> candidate.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();

        final NamedTopic topic = (NamedTopic) constructor.call1("orders");

        assertThat(topic.name).isEqualTo("orders");
    }

    public static final class NamedTopic {
        private final String name;

        public NamedTopic(String name) {
            this.name = name;
        }
    }
}
