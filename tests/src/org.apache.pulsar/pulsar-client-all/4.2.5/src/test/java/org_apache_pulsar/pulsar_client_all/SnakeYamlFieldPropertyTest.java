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
import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.BeanAccess;
import org.junit.jupiter.api.Test;

public class SnakeYamlFieldPropertyTest {
    @Test
    void readsAndWritesFieldBackedProperty() {
        Constructor constructor = new Constructor(FieldSettings.class, new LoaderOptions());
        constructor.getPropertyUtils().setBeanAccess(BeanAccess.FIELD);
        Yaml yaml = new Yaml(constructor);

        FieldSettings settings = yaml.load("name: field-value");

        assertThat(settings.name).isEqualTo("field-value");
        assertThat(yaml.dump(settings)).contains("name: field-value");
    }

    public static class FieldSettings {
        public String name;
    }
}
