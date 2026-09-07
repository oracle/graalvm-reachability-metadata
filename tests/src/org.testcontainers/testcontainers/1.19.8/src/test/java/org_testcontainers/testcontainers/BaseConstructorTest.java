/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseConstructorTest {
    @Test
    void createsTypedArraysAndBeanInstances() {
        Yaml yaml = new Yaml();

        assertThat(yaml.loadAs("[one, two]", String[].class)).containsExactly("one", "two");
        assertThat(yaml.loadAs("value: constructed", Bean.class).value).isEqualTo("constructed");
    }

    public static class Bean {
        public String value;
    }
}
