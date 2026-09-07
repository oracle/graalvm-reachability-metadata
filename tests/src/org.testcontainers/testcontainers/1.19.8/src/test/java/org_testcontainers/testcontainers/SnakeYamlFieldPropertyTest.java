/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;
import org.testcontainers.shaded.org.yaml.snakeyaml.introspector.BeanAccess;

import static org.assertj.core.api.Assertions.assertThat;

public class SnakeYamlFieldPropertyTest {
    @Test
    void readsAndWritesFieldsWhenFieldAccessIsSelected() {
        Yaml yaml = new Yaml();
        yaml.setBeanAccess(BeanAccess.FIELD);
        Bean bean = yaml.loadAs("message: loaded", Bean.class);

        assertThat(bean.message).isEqualTo("loaded");
        assertThat(yaml.dumpAsMap(bean)).contains("message: loaded");
    }

    public static class Bean {
        private String message;
    }
}
