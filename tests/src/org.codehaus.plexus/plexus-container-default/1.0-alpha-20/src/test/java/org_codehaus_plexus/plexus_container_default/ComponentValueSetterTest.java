/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.configurator.converters.ComponentValueSetter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComponentValueSetterTest {
    @Test
    public void configuresPrivateFieldWhenNoSetterExists() throws Exception {
        FieldOnlyComponent component = new FieldOnlyComponent();

        configure(component, "configuredValue", "field value");

        assertEquals("field value", component.getConfiguredValue());
    }

    @Test
    public void configuresUsingSetterWhenSetterExists() throws Exception {
        SetterOnlyComponent component = new SetterOnlyComponent();

        configure(component, "configuredValue", "setter value");

        assertEquals("setter value", component.getAssignedValue());
    }

    private static void configure(Object component, String fieldName, String value) throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration(fieldName);
        configuration.setValue(value);

        ComponentValueSetter setter = new ComponentValueSetter(fieldName, component, new DefaultConverterLookup());
        setter.configure(
            configuration,
            ComponentValueSetterTest.class.getClassLoader(),
            new LiteralExpressionEvaluator()
        );
    }

    public static final class FieldOnlyComponent {
        private String configuredValue;

        public String getConfiguredValue() {
            return configuredValue;
        }
    }

    public static final class SetterOnlyComponent {
        private String assignedValue;

        public void setConfiguredValue(String configuredValue) {
            assignedValue = configuredValue;
        }

        public String getAssignedValue() {
            return assignedValue;
        }
    }

    private static final class LiteralExpressionEvaluator implements ExpressionEvaluator {
        @Override
        public Object evaluate(String expression) throws ExpressionEvaluationException {
            return expression;
        }

        @Override
        public File alignToBaseDirectory(File file) {
            return file;
        }
    }
}
