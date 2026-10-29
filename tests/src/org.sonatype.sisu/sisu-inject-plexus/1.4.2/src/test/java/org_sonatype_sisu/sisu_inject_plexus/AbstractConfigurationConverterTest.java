/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AbstractConfigurationConverterTest {
    @Test
    void registeredConverterCreatesNamedEndpointAndConfiguresItsProperties() throws Exception {
        DefaultConverterLookup lookup = new DefaultConverterLookup();
        lookup.registerConverter(new NamedEndpointConverter());
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("endpoint");
        configuration.setValue(ConfiguredEndpoint.class.getName());
        configuration.addChild("address", "https://custom.example");

        Object result = lookup.lookupConverterForType(ConfiguredEndpoint.class).fromConfiguration(
                lookup, configuration, ConfiguredEndpoint.class, getClass(),
                getClass().getClassLoader(), new DefaultExpressionEvaluator());

        ConfiguredEndpoint endpoint = assertInstanceOf(ConfiguredEndpoint.class, result);
        assertEquals("https://custom.example", endpoint.getAddress());
    }

    @Test
    void instantiatesConfiguredImplementationAndPopulatesItsProperties() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("endpoint");
        configuration.setAttribute("implementation", ConfiguredEndpoint.class.getName());
        configuration.addChild("address", "https://configured.example");

        Object result = new ObjectWithFieldsConverter().fromConfiguration(
                new DefaultConverterLookup(), configuration, Object.class, getClass(),
                getClass().getClassLoader(), new DefaultExpressionEvaluator());

        ConfiguredEndpoint endpoint = assertInstanceOf(ConfiguredEndpoint.class, result);
        assertEquals("https://configured.example", endpoint.getAddress());
    }

    @Test
    void instantiatesDeclaredConcreteTypeWithoutImplementationHint() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("endpoint");
        configuration.addChild("address", "https://default.example");

        ConfiguredEndpoint endpoint = (ConfiguredEndpoint) new ObjectWithFieldsConverter().fromConfiguration(
                new DefaultConverterLookup(), configuration, ConfiguredEndpoint.class, getClass(),
                getClass().getClassLoader(), new DefaultExpressionEvaluator());

        assertEquals("https://default.example", endpoint.getAddress());
    }

    @SuppressWarnings("rawtypes")
    private static class NamedEndpointConverter extends AbstractConfigurationConverter {
        @Override
        public boolean canConvert(Class type) {
            return type == ConfiguredEndpoint.class;
        }

        @Override
        public Object fromConfiguration(ConverterLookup lookup, PlexusConfiguration configuration,
                                        Class type, Class baseType, ClassLoader classLoader,
                                        ExpressionEvaluator evaluator, ConfigurationListener listener)
                throws ComponentConfigurationException {
            ConfiguredEndpoint endpoint = (ConfiguredEndpoint) instantiateObject(configuration.getValue(), classLoader);
            new ObjectWithFieldsConverter().processConfiguration(
                    lookup, endpoint, classLoader, configuration, evaluator, listener);
            return endpoint;
        }
    }
}
