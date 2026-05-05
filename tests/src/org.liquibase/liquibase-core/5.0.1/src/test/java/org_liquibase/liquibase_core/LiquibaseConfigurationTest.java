/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LiquibaseConfigurationTest {

    @SuppressWarnings("deprecation")
    @Test
    void getConfigurationCreatesDeprecatedConfigurationContainerInstance() {
        LiquibaseConfiguration configuration = LiquibaseConfiguration.getInstance();

        GlobalConfiguration globalConfiguration = configuration.getConfiguration(GlobalConfiguration.class);

        assertThat(globalConfiguration).isNotNull();
        assertThat(globalConfiguration.getNamespace()).isEqualTo("liquibase");
    }
}
