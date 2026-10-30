/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.DefaultExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class AbstractComponentConfiguratorTest {
    @Test
    void adaptsListenerAwareLegacyConfigurator() throws Exception {
        ClassWorld world = new ClassWorld("legacy-listener", getClass().getClassLoader());
        try (ClassRealm realm = world.getRealm("legacy-listener")) {
            AbstractComponentConfigurator configurator = new LegacyConfigurators.WithListener();
            Options options = new Options();
            ChangeListener listener = new ChangeListener();
            XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
            configuration.addChild("message", "with listener");

            configurator.configureComponent(options, configuration, new DefaultExpressionEvaluator(), realm, listener);

            assertEquals("with listener", options.getMessage());
            assertEquals("message", listener.property);
            assertEquals("with listener", listener.value);
            assertSame(options, listener.bean);
        } finally {
            world.disposeRealm("legacy-listener");
        }
    }

    @Test
    void adaptsLegacyConfiguratorWithoutListenerOverload() throws Exception {
        ClassWorld world = new ClassWorld("legacy-basic", getClass().getClassLoader());
        try (ClassRealm realm = world.getRealm("legacy-basic")) {
            AbstractComponentConfigurator configurator = new LegacyConfigurators.Basic();
            Options options = new Options();
            XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
            configuration.addChild("message", "legacy value");

            configurator.configureComponent(options, configuration, realm);

            assertEquals("legacy value", options.getMessage());
        } finally {
            world.disposeRealm("legacy-basic");
        }
    }

    public static class Options {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    private static class ChangeListener implements ConfigurationListener {
        private String property;
        private Object value;
        private Object bean;

        @Override
        public void notifyFieldChangeUsingSetter(String name, Object newValue, Object target) {
            property = name;
            value = newValue;
            bean = target;
        }

        @Override
        public void notifyFieldChangeUsingReflection(String name, Object newValue, Object target) {
            property = name;
            value = newValue;
            bean = target;
        }
    }
}
