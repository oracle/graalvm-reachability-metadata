/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationServiceImplTest {

    @Test
    public void instantiatesClassValuedSettings() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("application.setting", ConfiguredValue.class.getName())
                .build();
        try {
            ConfigurationService service = registry.getService(ConfigurationService.class);

            ConfiguredValue value = service.getSetting(
                    "application.setting",
                    ConfiguredValue.class,
                    null
            );

            assertThat(value.getValue()).isEqualTo("configured");
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class ConfiguredValue {
        public String getValue() {
            return "configured";
        }
    }
}
