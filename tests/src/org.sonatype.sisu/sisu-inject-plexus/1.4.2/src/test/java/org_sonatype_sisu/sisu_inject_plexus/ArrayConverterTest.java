/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.component.configurator.converters.composite.ArrayConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArrayConverterTest {
    @Test
    void resolvesQualifiedAndPackageRelativeElementTypes() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("endpoints");
        PlexusConfiguration qualified = configuration.getChild(ConfiguredEndpoint.class.getName());
        qualified.addChild("address", "https://east.example");
        PlexusConfiguration relative = configuration.getChild("configured-endpoint");
        relative.addChild("address", "https://west.example");

        ConfiguredEndpoint[] endpoints = (ConfiguredEndpoint[]) new ArrayConverter().fromConfiguration(
                new DefaultConverterLookup(), configuration, ConfiguredEndpoint[].class, getClass(),
                getClass().getClassLoader(), new DefaultExpressionEvaluator());

        assertEquals(2, endpoints.length);
        assertEquals("https://east.example", endpoints[0].getAddress());
        assertEquals("https://west.example", endpoints[1].getAddress());
    }

    @Test
    void usesArrayComponentTypeForPlainElements() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("regions");
        configuration.addChild("region", "east");
        configuration.addChild("region", "west");

        String[] regions = (String[]) new ArrayConverter().fromConfiguration(
                new DefaultConverterLookup(), configuration, String[].class, getClass(),
                getClass().getClassLoader(), new DefaultExpressionEvaluator());

        assertArrayEquals(new String[]{"east", "west"}, regions);
    }
}
