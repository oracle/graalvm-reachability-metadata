/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kerby.config.Conf;
import org.junit.jupiter.api.Test;

public class ConfigImplTest {
    @Test
    void resolvesAndInstantiatesConfiguredClassNames() throws Exception {
        Conf conf = new Conf();
        String className = ExampleValue.class.getName();
        conf.setString(className, "configured");

        assertThat(conf.getClass(className)).isEqualTo(ExampleValue.class);

        ExampleValue instance = conf.getInstance(className, ExampleValue.class);

        assertThat(instance).isNotNull();
        assertThat(instance.describe()).isEqualTo("created");
    }

    public static final class ExampleValue {
        public ExampleValue() {
        }

        public String describe() {
            return "created";
        }
    }
}
