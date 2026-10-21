/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.LoaderOptions;
import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.constructor.Constructor;
import org.junit.jupiter.api.Test;

public class ConstructorInnerConstructSequenceTest {
    @Test
    void createsImmutableValueThroughSequenceConstructor() {
        Yaml yaml = new Yaml(new Constructor(SequenceValue.class, new LoaderOptions()));

        SequenceValue value = yaml.load("[message, 5]");

        assertThat(value.text).isEqualTo("message");
        assertThat(value.priority).isEqualTo(5);
    }

    public static class SequenceValue {
        private final String text;
        private final int priority;

        public SequenceValue(String text, int priority) {
            this.text = text;
            this.priority = priority;
        }
    }
}
