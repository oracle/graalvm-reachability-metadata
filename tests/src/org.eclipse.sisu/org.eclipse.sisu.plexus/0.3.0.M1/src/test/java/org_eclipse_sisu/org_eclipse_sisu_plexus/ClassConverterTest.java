/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;

import org.codehaus.plexus.component.configurator.converters.basic.ClassConverter;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class ClassConverterTest {
    @Test
    void convertsConfiguredClassName() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("mapType");
        configuration.setValue("java.util.LinkedHashMap");

        Object type = ConfigurationSupport.convert(new ClassConverter(), configuration, Class.class, getClass());

        assertSame(LinkedHashMap.class, type);
    }
}
