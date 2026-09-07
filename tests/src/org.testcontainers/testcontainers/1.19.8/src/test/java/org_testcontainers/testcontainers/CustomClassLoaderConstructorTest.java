/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;
import org.testcontainers.shaded.org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomClassLoaderConstructorTest {
    @Test
    void resolvesKnownYamlTagsWithTheConfiguredApplicationLoader() {
        CustomClassLoaderConstructor constructor = new CustomClassLoaderConstructor(getClass().getClassLoader());
        String yaml = "!!" + Bean.class.getName() + " {value: loaded}";

        Object loaded = new Yaml(constructor).load(yaml);

        assertThat(loaded).isInstanceOfSatisfying(Bean.class, bean -> assertThat(bean.value).isEqualTo("loaded"));
    }

    public static class Bean {
        public String value;
    }
}
