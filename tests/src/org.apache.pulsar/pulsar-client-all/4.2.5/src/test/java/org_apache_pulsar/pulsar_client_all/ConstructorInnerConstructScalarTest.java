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

public class ConstructorInnerConstructScalarTest {
    @Test
    void createsScalarThroughSingleArgumentConstructor() {
        Yaml yaml = new Yaml(new Constructor(ScalarValue.class, new LoaderOptions()));

        ScalarValue value = yaml.load("message");

        assertThat(value.text).isEqualTo("message");
    }

    public static class ScalarValue {
        private final String text;

        public ScalarValue(String text) {
            this.text = text;
        }
    }
}
