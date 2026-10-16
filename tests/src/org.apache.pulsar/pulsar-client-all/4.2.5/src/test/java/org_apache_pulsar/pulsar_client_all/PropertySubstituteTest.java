/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.PropertySubstitute;
import org.junit.jupiter.api.Test;

public class PropertySubstituteTest {
    @Test
    void discoversAndInvokesConfiguredAccessors() throws Exception {
        final PropertySubstitute property = new PropertySubstitute(
                "topic", String.class, "getTopic", "setTopic");
        property.setTargetType(Configuration.class);
        final Configuration configuration = new Configuration();

        property.set(configuration, "persistent://tenant/ns/events");

        assertThat(property.get(configuration)).isEqualTo("persistent://tenant/ns/events");
        assertThat(property.isReadable()).isTrue();
        assertThat(property.isWritable()).isTrue();
    }

    public static final class Configuration {
        private String topic;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String configuredTopic) {
            topic = configuredTopic;
        }
    }
}
