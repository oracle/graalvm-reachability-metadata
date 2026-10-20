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
import org.apache.pulsar.shade.org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import org.junit.jupiter.api.Test;

public class CustomClassLoaderConstructorTest {
    @Test
    void resolvesExplicitYamlTagWithConfiguredLoader() {
        CustomClassLoaderConstructor constructor = new CustomClassLoaderConstructor(
                getClass().getClassLoader(), new LoaderOptions());
        String yaml = "!!" + Settings.class.getName() + " {mode: persistent}";

        Settings settings = new Yaml(constructor).load(yaml);

        assertThat(settings.mode).isEqualTo("persistent");
    }

    public static class Settings {
        public String mode;
    }
}
