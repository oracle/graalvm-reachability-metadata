/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.extensions.compactnotation.PackageCompactConstructor;
import org.junit.jupiter.api.Test;

public class PackageCompactConstructorTest {
    @Test
    void resolvesCompactTypeRelativeToConfiguredPackage() {
        PackageCompactConstructor constructor =
                new PackageCompactConstructor(getClass().getPackageName());
        String simpleBinaryName = getClass().getSimpleName() + "$CompactValue";

        CompactValue value = new Yaml(constructor).load(simpleBinaryName + "(persistent)");

        assertThat(value.mode).isEqualTo("persistent");
    }

    public static class CompactValue {
        private final String mode;

        public CompactValue(String mode) {
            this.mode = mode;
        }
    }
}
