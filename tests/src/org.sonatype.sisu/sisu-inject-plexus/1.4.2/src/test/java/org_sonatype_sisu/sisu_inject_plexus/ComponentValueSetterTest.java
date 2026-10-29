/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.ComponentValueSetter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComponentValueSetterTest {
    @Test
    void configuresSetterAndReportsTheConvertedValue() throws Exception {
        ConfiguredEndpoint endpoint = new ConfiguredEndpoint();
        Changes changes = new Changes();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("address");
        configuration.setValue("https://setter.example");

        new ComponentValueSetter("address", endpoint, new DefaultConverterLookup(), changes)
                .configure(configuration, getClass().getClassLoader(), new DefaultExpressionEvaluator());

        assertEquals("https://setter.example", endpoint.getAddress());
        assertEquals(List.of("setter:address=https://setter.example"), changes.events);
    }

    @Test
    void configuresPrivateFieldWithoutSetterAndReportsTheConvertedValue() throws Exception {
        WorkerOptions options = new WorkerOptions();
        Changes changes = new Changes();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("workers");
        configuration.setValue("7");

        new ComponentValueSetter("workers", options, new DefaultConverterLookup(), changes)
                .configure(configuration, getClass().getClassLoader(), new DefaultExpressionEvaluator());

        assertEquals(7, options.getWorkers());
        assertEquals(List.of("field:workers=7"), changes.events);
    }

    public static class WorkerOptions {
        private int workers;

        public int getWorkers() {
            return workers;
        }
    }

    private static class Changes implements ConfigurationListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void notifyFieldChangeUsingSetter(String fieldName, Object value, Object target) {
            events.add("setter:" + fieldName + "=" + value);
        }

        @Override
        public void notifyFieldChangeUsingReflection(String fieldName, Object value, Object target) {
            events.add("field:" + fieldName + "=" + value);
        }
    }
}
