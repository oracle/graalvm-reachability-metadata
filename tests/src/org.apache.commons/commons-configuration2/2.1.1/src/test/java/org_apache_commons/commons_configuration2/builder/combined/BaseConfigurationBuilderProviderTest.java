/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.builder.combined;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.SystemConfiguration;
import org.apache.commons.configuration2.builder.BasicBuilderParameters;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.BuilderParameters;
import org.apache.commons.configuration2.builder.combined.BaseConfigurationBuilderProvider;
import org.apache.commons.configuration2.builder.combined.CombinedConfigurationBuilder;
import org.apache.commons.configuration2.builder.combined.ConfigurationDeclaration;
import org.junit.jupiter.api.Test;

public class BaseConfigurationBuilderProviderTest {
    @Test
    void createsParameterObjectAndBuilderFromConfiguredClassNames()
            throws Exception {
        final TestableBaseConfigurationBuilderProvider provider =
                new TestableBaseConfigurationBuilderProvider();
        final Collection<BuilderParameters> parameters =
                provider.newParameterObjects();
        final ConfigurationDeclaration declaration = new ConfigurationDeclaration(
                new CombinedConfigurationBuilder(), new BaseHierarchicalConfiguration());

        final BasicConfigurationBuilder<? extends Configuration> builder =
                provider.newBuilder(declaration, parameters);

        assertThat(parameters).hasSize(1);
        assertThat(parameters.iterator().next())
                .isInstanceOf(BasicBuilderParameters.class);
        assertThat(builder.getResultClass()).isEqualTo(SystemConfiguration.class);
        assertThat(builder.isAllowFailOnInit()).isFalse();
    }

    private static final class TestableBaseConfigurationBuilderProvider
            extends BaseConfigurationBuilderProvider {
        private static final String BASIC_BUILDER_CLASS =
                "org.apache.commons.configuration2.builder.BasicConfigurationBuilder";
        private static final String SYSTEM_CONFIGURATION_CLASS =
                "org.apache.commons.configuration2.SystemConfiguration";
        private static final String BASIC_PARAMETERS_CLASS =
                "org.apache.commons.configuration2.builder.BasicBuilderParameters";

        TestableBaseConfigurationBuilderProvider() {
            super(BASIC_BUILDER_CLASS, null, SYSTEM_CONFIGURATION_CLASS,
                    Collections.singletonList(BASIC_PARAMETERS_CLASS));
        }

        Collection<BuilderParameters> newParameterObjects() throws Exception {
            return createParameterObjects();
        }

        BasicConfigurationBuilder<? extends Configuration> newBuilder(
                ConfigurationDeclaration declaration,
                Collection<BuilderParameters> parameters) throws Exception {
            return createBuilder(declaration, parameters);
        }
    }
}
