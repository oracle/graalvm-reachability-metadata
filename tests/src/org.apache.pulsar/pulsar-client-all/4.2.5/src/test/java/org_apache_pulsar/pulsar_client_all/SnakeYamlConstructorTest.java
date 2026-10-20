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

public class SnakeYamlConstructorTest {
    @Test
    void loadsNamedRootClass() throws Exception {
        Constructor constructor = new Constructor(Settings.class.getName(), new LoaderOptions());

        Settings settings = new Yaml(constructor).load("mode: persistent\nretries: 4");

        assertThat(settings.mode).isEqualTo("persistent");
        assertThat(settings.retries).isEqualTo(4);
    }

    public static class Settings {
        public String mode;
        public int retries;
    }
}
