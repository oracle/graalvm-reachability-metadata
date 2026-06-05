/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.configuration.ConfigurationContainer;
import liquibase.configuration.ConfigurationProperty;
import liquibase.configuration.LiquibaseConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class LiquibaseConfigurationTest {

    @Test
    void getConfigurationInstantiatesLegacyConfigurationContainer() {
        LegacyConfigurationContainer configuration = LiquibaseConfiguration.getInstance()
                .getConfiguration(LegacyConfigurationContainer.class);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getNamespace()).isEqualTo("reachability-test");
    }

    public static final class LegacyConfigurationContainer implements ConfigurationContainer {
        public LegacyConfigurationContainer() {
        }

        @Override
        public ConfigurationProperty getProperty(String propertyName) {
            return null;
        }

        @Override
        public Set<ConfigurationProperty> getProperties() {
            return Collections.emptySet();
        }

        @Override
        public <T> T getValue(String propertyName, Class<T> returnType) {
            return null;
        }

        @Override
        public void setValue(String propertyName, Object value) {
        }

        @Override
        public String getNamespace() {
            return "reachability-test";
        }
    }
}
