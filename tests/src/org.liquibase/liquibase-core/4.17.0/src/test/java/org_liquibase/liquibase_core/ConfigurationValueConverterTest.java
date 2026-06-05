/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.configuration.ConfigurationDefinition;
import liquibase.configuration.ConfigurationValueConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationValueConverterTest {

    @Test
    void classConfigurationValueLoadsConfiguredClassName() {
        String configurationKey = "reachability.test.targetType";
        ConfigurationDefinition<Class> definition = new ConfigurationDefinition.Builder("reachability.test")
                .define("targetType", Class.class)
                .setValueHandler(ConfigurationValueConverter.CLASS)
                .buildTemporary();

        String previousValue = System.getProperty(configurationKey);
        try {
            System.setProperty(configurationKey, "java.lang.String");

            assertThat(definition.getCurrentValue().getName()).isEqualTo("java.lang.String");
        } finally {
            if (previousValue == null) {
                System.clearProperty(configurationKey);
            } else {
                System.setProperty(configurationKey, previousValue);
            }
        }
    }
}
