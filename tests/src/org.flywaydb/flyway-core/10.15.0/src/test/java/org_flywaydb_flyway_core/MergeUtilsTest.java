/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import org.flywaydb.core.internal.configuration.models.ConfigurationModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MergeUtilsTest {

    @Test
    void mergesConfigurationModelsWithOverrideValues() {
        ConfigurationModel base = ConfigurationModel.defaults();
        base.setId("base");
        base.getFlyway().setTable("base_history");

        ConfigurationModel override = ConfigurationModel.defaults();
        override.setId("override");
        override.getFlyway().setTable("override_history");

        ConfigurationModel merged = base.merge(override);

        assertThat(merged.getId()).isEqualTo("override");
        assertThat(merged.getFlyway().getTable()).isEqualTo("override_history");
        assertThat(merged.getEnvironments()).containsKey("default");
    }
}
