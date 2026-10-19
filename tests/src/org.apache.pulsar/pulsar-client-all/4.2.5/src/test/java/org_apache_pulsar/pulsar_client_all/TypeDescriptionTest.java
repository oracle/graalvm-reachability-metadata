/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.LoaderOptions;
import org.apache.pulsar.shade.org.yaml.snakeyaml.TypeDescription;
import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.constructor.Constructor;
import org.junit.jupiter.api.Test;

public class TypeDescriptionTest {
    @Test
    void createsDescribedTypeAndAppliesProperties() {
        TypeDescription description = new TypeDescription(DescribedSettings.class);
        Yaml yaml = new Yaml(new Constructor(description, new LoaderOptions()));

        DescribedSettings settings = yaml.load("name: configured");

        assertThat(settings.name).isEqualTo("configured");
    }

    public static class DescribedSettings {
        public String name;
    }
}
