/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractComponentConfiguratorTest {
    @Test
    void dispatchesToLegacyConfiguratorWithListener() throws Exception {
        ClassWorld world = new ClassWorld("legacy-listener", getClass().getClassLoader());
        try {
            ComponentConfigurator configurator = new ListeningConfigurator();
            ConfiguredEndpoint endpoint = new ConfiguredEndpoint();
            XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
            configuration.addChild("address", "https://listener.example");
            Changes changes = new Changes();

            configurator.configureComponent(endpoint, configuration, new DefaultExpressionEvaluator(),
                    world.getRealm("legacy-listener"), changes);

            assertEquals("https://listener.example", endpoint.getAddress());
            assertEquals(List.of("address=https://listener.example"), changes.events);
        } finally {
            world.disposeRealm("legacy-listener");
        }
    }

    @Test
    void dispatchesToLegacyConfiguratorWithoutListener() throws Exception {
        ClassWorld world = new ClassWorld("legacy-simple", getClass().getClassLoader());
        try {
            ComponentConfigurator configurator = new SimpleConfigurator();
            ConfiguredEndpoint endpoint = new ConfiguredEndpoint();
            XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
            configuration.addChild("address", "https://simple.example");

            configurator.configureComponent(endpoint, configuration, world.getRealm("legacy-simple"));

            assertEquals("https://simple.example", endpoint.getAddress());
        } finally {
            world.disposeRealm("legacy-simple");
        }
    }

    public static class ListeningConfigurator extends AbstractComponentConfigurator {
        public void configureComponent(Object component, PlexusConfiguration configuration,
                                       ExpressionEvaluator evaluator, ClassRealm realm, ConfigurationListener listener)
                throws ComponentConfigurationException {
            new ObjectWithFieldsConverter().processConfiguration(converterLookup, component,
                    realm.getClassLoader(), configuration, evaluator, listener);
        }
    }

    public static class SimpleConfigurator extends AbstractComponentConfigurator {
        public void configureComponent(Object component, PlexusConfiguration configuration,
                                       ExpressionEvaluator evaluator, ClassRealm realm)
                throws ComponentConfigurationException {
            new ObjectWithFieldsConverter().processConfiguration(converterLookup, component,
                    realm.getClassLoader(), configuration, evaluator);
        }
    }

    private static class Changes implements ConfigurationListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void notifyFieldChangeUsingSetter(String fieldName, Object value, Object target) {
            events.add(fieldName + "=" + value);
        }

        @Override
        public void notifyFieldChangeUsingReflection(String fieldName, Object value, Object target) {
            events.add(fieldName + "=" + value);
        }
    }
}
