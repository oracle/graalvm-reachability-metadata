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

public class BaseConstructorTest {
    @Test
    void createsBeanAndTypedArray() {
        Yaml yaml = new Yaml(new Constructor(ArraySettings.class, new LoaderOptions()));

        ArraySettings settings = yaml.load("values: [one, two]");

        assertThat(settings.values).containsExactly("one", "two");
        assertThat(settings.values.getClass()).isEqualTo(String[].class);
    }

    public static class ArraySettings {
        public String[] values;
    }
}
