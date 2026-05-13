/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.ComponentValueSetter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.jupiter.api.Test;

public class ComponentValueSetterTest {
    @Test
    void configuresPrivateFieldWhenNoSetterExists() throws ComponentConfigurationException {
        final FieldOnlyComponent component = new FieldOnlyComponent();

        configure(component, "configuredValue", "field value");

        assertThat(component.getConfiguredValue()).isEqualTo("field value");
    }

    @Test
    void configuresUsingSetterWhenSetterExists() throws ComponentConfigurationException {
        final SetterOnlyComponent component = new SetterOnlyComponent();

        configure(component, "configuredValue", "setter value");

        assertThat(component.getAssignedValue()).isEqualTo("setter value");
    }

    private static void configure(final Object component, final String fieldName, final String value)
        throws ComponentConfigurationException {
        final PlexusConfiguration configuration = new DefaultPlexusConfiguration(fieldName, value);
        final ComponentValueSetter setter = new ComponentValueSetter(
                fieldName, component, new DefaultConverterLookup());

        setter.configure(
                configuration,
                ComponentValueSetterTest.class.getClassLoader(),
                new DefaultExpressionEvaluator());
    }

    public static final class FieldOnlyComponent {
        private String configuredValue;

        public String getConfiguredValue() {
            return configuredValue;
        }
    }

    public static final class SetterOnlyComponent {
        private String assignedValue;

        public void setConfiguredValue(final String configuredValue) {
            assignedValue = configuredValue;
        }

        public String getAssignedValue() {
            return assignedValue;
        }
    }
}
