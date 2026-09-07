/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;
import org.testcontainers.shaded.org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor;

import static org.assertj.core.api.Assertions.assertThat;

public class CompactConstructorTest {
    @Test
    void createsObjectsExpressedWithCompactNotation() {
        String input = Bean.class.getName() + "(compact)";

        Bean bean = new Yaml(new CompactConstructor()).load(input);

        assertThat(bean.value).isEqualTo("compact");
    }

    public static class Bean {
        private final String value;

        public Bean(String value) {
            this.value = value;
        }
    }
}
