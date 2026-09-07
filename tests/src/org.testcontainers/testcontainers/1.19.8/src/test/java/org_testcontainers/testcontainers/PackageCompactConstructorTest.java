/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;
import org.testcontainers.shaded.org.yaml.snakeyaml.extensions.compactnotation.PackageCompactConstructor;

import static org.assertj.core.api.Assertions.assertThat;

public class PackageCompactConstructorTest {
    @Test
    void resolvesCompactTypesRelativeToAConfiguredPackage() {
        PackageCompactConstructor constructor = new PackageCompactConstructor(getClass().getPackageName());

        String compactType = PackageCompactConstructorTest.class.getSimpleName() + "$PackageBean(packaged)";
        PackageBean bean = new Yaml(constructor).load(compactType);

        assertThat(bean.value).isEqualTo("packaged");
    }

    public static class PackageBean {
        private final String value;

        public PackageBean(String value) {
            this.value = value;
        }
    }
}
