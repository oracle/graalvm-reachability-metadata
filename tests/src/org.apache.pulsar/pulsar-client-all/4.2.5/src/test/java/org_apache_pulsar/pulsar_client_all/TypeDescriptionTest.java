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
    void constructsTheConfiguredImplementationType() {
        final TypeDescription description = new TypeDescription(Service.class, ServiceConfiguration.class);
        final Constructor constructor = new Constructor(description, new LoaderOptions());

        final Service service = new Yaml(constructor).loadAs("name: ledger", Service.class);

        assertThat(service).isInstanceOf(ServiceConfiguration.class);
        assertThat(service.getName()).isEqualTo("ledger");
    }

    public interface Service {
        String getName();

        void setName(String name);
    }

    public static final class ServiceConfiguration implements Service {
        private String name;

        public ServiceConfiguration() {
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }
}
