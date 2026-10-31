/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractComponentConfiguratorTest {
    @Test
    public void configuresLegacyExtensionUsingDefaultExpressionEvaluator() throws Exception {
        ComponentConfigurator configurator = new LegacyConfigurator();
        NamedComponent component = new NamedComponent();
        ClassWorld world = new ClassWorld("legacy-default", getClass().getClassLoader());

        try {
            configurator.configureComponent(component, configuration("mail-service"), world.getRealm("legacy-default"));

            assertEquals("mail-service", component.getName());
        } finally {
            world.disposeRealm("legacy-default");
        }
    }

    @Test
    public void forwardsExpressionEvaluatorRealmAndListenerToLegacyExtension() throws Exception {
        LegacyConfigurator legacyConfigurator = new LegacyConfigurator();
        ComponentConfigurator configurator = legacyConfigurator;
        NamedComponent component = new NamedComponent();
        RecordingListener listener = new RecordingListener();
        ExpressionEvaluator evaluator = new DefaultExpressionEvaluator() {
            @Override
            public Object evaluate(String expression) {
                return "${service.name}".equals(expression) ? "resolved-service" : expression;
            }
        };
        ClassWorld world = new ClassWorld("legacy-listener", getClass().getClassLoader());

        try {
            configurator.configureComponent(
                component, configuration("${service.name}"), evaluator, world.getRealm("legacy-listener"), listener
            );

            assertEquals("resolved-service", component.getName());
            assertEquals(1, legacyConfigurator.invocations);
            assertEquals("legacy-listener", legacyConfigurator.realm.getId());
            assertSame(world.getRealm("legacy-listener"), legacyConfigurator.realm.getClassLoader());
            assertEquals("name", listener.fieldName);
            assertEquals("resolved-service", listener.value);
            assertSame(component, listener.target);
        } finally {
            world.disposeRealm("legacy-listener");
        }
    }

    private static XmlPlexusConfiguration configuration(String name) {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        XmlPlexusConfiguration child = new XmlPlexusConfiguration("name");
        child.setValue(name);
        configuration.addChild(child);
        return configuration;
    }

    /// A configurator extension implementing the legacy ClassWorlds signature.
    public static final class LegacyConfigurator extends AbstractComponentConfigurator {
        private ClassRealm realm;
        private int invocations;

        public void configureComponent(Object component, PlexusConfiguration configuration,
                                       ExpressionEvaluator evaluator, ClassRealm containerRealm,
                                       ConfigurationListener listener) throws ComponentConfigurationException {
            realm = containerRealm;
            invocations++;
            new ObjectWithFieldsConverter().processConfiguration(
                converterLookup, component, containerRealm.getClassLoader(), configuration, evaluator, listener
            );
        }
    }

    public static final class NamedComponent {
        private String name;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final class RecordingListener implements ConfigurationListener {
        private String fieldName;
        private Object value;
        private Object target;

        @Override
        public void notifyFieldChangeUsingSetter(String name, Object assignedValue, Object component) {
            fieldName = name;
            value = assignedValue;
            target = component;
        }

        @Override
        public void notifyFieldChangeUsingReflection(String name, Object assignedValue, Object component) {
            throw new AssertionError("Configuration must use the public setter");
        }
    }
}
