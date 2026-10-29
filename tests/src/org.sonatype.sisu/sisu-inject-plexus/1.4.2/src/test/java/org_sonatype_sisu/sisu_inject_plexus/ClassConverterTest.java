/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.component.configurator.converters.basic.ClassConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassConverterTest {
    @Test
    void resolvesClassNameFromConfiguration() throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("implementation-type");
        configuration.setValue("java.util.LinkedHashMap");

        Object type = new ClassConverter().fromConfiguration(new DefaultConverterLookup(), configuration,
                Class.class, getClass(), getClass().getClassLoader(), new DefaultExpressionEvaluator());

        assertSame(LinkedHashMap.class, type);
    }
}
