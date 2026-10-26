/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor;
import org.junit.jupiter.api.Test;

public class CompactConstructorTest {
    @Test
    void createsAndConfiguresCompactNotationValue() {
        Yaml yaml = new Yaml(new CompactConstructor());
        String typeName = CompactValue.class.getName();

        CompactValue value = yaml.load(typeName + "(persistent, retries=4)");

        assertThat(value.mode).isEqualTo("persistent");
        assertThat(value.retries).isEqualTo("4");
    }

    public static class CompactValue {
        private final String mode;
        private String retries;

        public CompactValue(String mode) {
            this.mode = mode;
        }

        public void setRetries(String retries) {
            this.retries = retries;
        }
    }
}
