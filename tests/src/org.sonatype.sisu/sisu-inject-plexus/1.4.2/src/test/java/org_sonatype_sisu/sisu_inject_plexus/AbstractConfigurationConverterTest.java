/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AbstractConfigurationConverterTest {
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
}
