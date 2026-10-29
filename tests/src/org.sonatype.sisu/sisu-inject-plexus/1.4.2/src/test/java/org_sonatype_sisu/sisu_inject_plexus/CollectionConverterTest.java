/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.component.configurator.converters.composite.CollectionConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CollectionConverterTest {
    @Test
    void instantiatesConcreteCollectionAndResolvesElementTypes() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("endpoints");
        PlexusConfiguration qualified = configuration.getChild(ConfiguredEndpoint.class.getName());
        qualified.addChild("address", "https://east.example");
        PlexusConfiguration relative = configuration.getChild("configured-endpoint");
        relative.addChild("address", "https://west.example");

        Object result = convert(configuration, ArrayList.class);
        List<?> endpoints = assertInstanceOf(ArrayList.class, result);
        assertEquals(2, endpoints.size());
        assertEquals("https://east.example", assertInstanceOf(ConfiguredEndpoint.class, endpoints.get(0)).getAddress());
        assertEquals("https://west.example", assertInstanceOf(ConfiguredEndpoint.class, endpoints.get(1)).getAddress());
    }

    @Test
    void usesExplicitCollectionImplementationWithStringElements() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("regions");
        configuration.setAttribute("implementation", LinkedList.class.getName());
        configuration.addChild("java.lang.String", "east");
        configuration.addChild("java.lang.String", "west");

        Object result = convert(configuration, List.class);
        assertInstanceOf(LinkedList.class, result);
        assertEquals(List.of("east", "west"), result);
    }

    @Test
    void suppliesDefaultListAndInfersPlainStringValues() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("regions");
        configuration.addChild("region", "north");
        configuration.addChild("region", "south");

        assertEquals(List.of("north", "south"), convert(configuration, List.class));
    }

    private Object convert(PlexusConfiguration configuration, Class<?> type) throws Exception {
        return new CollectionConverter().fromConfiguration(new DefaultConverterLookup(), configuration,
                type, getClass(), getClass().getClassLoader(), new DefaultExpressionEvaluator());
    }
}
