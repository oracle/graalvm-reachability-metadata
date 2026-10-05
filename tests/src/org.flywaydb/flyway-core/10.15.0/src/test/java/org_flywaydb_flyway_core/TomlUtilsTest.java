/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import java.util.Map;

import org.flywaydb.core.internal.configuration.TomlUtils;
import org.flywaydb.core.internal.configuration.models.ConfigurationModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TomlUtilsTest {

    @Test
    void convertsCommandLinePropertiesIntoConfigurationModel() {
        ConfigurationModel configuration = TomlUtils.loadConfigurationFromCommandlineArgs(
                Map.of("flyway.table", "schema_history"));

        assertThat(configuration.getFlyway().getTable()).isEqualTo("schema_history");
    }
}
