/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.TypeDescription;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;
import org.testcontainers.shaded.org.yaml.snakeyaml.constructor.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionTest {
    @Test
    void createsTheRootTypeDescribedToTheConstructor() {
        TypeDescription description = new TypeDescription(Bean.class);
        Bean bean = new Yaml(new Constructor(description)).load("value: typed");

        assertThat(bean.value).isEqualTo("typed");
    }

    public static class Bean {
        public String value;
    }
}
