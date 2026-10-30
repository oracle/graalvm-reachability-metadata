/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.codehaus.plexus.component.configurator.converters.composite.ArrayConverter;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class ArrayConverterTest {
    @Test
    void buildsTypedArrayFromChildValues() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("ports");
        configuration.addChild("port", "8080");
        configuration.addChild("port", "9090");

        Integer[] ports = (Integer[]) ConfigurationSupport.convert(new ArrayConverter(), configuration,
                Integer[].class, getClass());

        assertArrayEquals(new Integer[]{8080, 9090}, ports);
    }

    @Test
    void createsEmptyArrayWithRequestedComponentType() throws Exception {
        String[] names = (String[]) ConfigurationSupport.convert(new ArrayConverter(),
                new XmlPlexusConfiguration("names"), String[].class, getClass());

        assertArrayEquals(new String[0], names);
    }
}
