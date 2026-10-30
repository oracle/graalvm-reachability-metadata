/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.codehaus.plexus.component.configurator.converters.composite.CollectionConverter;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class AbstractCollectionConverterTest {
    @Test
    void determinesElementTypesFromQualifiedAndPackageRelativeNames() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("values");
        configuration.addChild("java.lang.Integer", "17");
        configuration.addChild("string", "release");

        Object values = ConfigurationSupport.convert(new CollectionConverter(), configuration, List.class,
                String.class);

        assertEquals(List.of(17, "release"), values);
    }
}
