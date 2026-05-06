/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.component.MapOrientedComponent;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.MapOrientedComponentConfigurator;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapOrientedComponentConfiguratorTest {
    @Test
    public void rejectsComponentsThatDoNotAcceptMapConfiguration() throws Exception {
        clearCompilerGeneratedClassCache();
        MapOrientedComponentConfigurator configurator = new MapOrientedComponentConfigurator();
        Object component = nonMapOrientedComponentSelectedAtRuntime();

        ComponentConfigurationException exception = assertThrows(
            ComponentConfigurationException.class,
            () -> configurator.configureComponent(component, new XmlPlexusConfiguration("configuration"), null, null)
        );

        assertTrue(exception.getMessage().contains("can only process implementations"));
        assertTrue(exception.getMessage().contains(MapOrientedComponent.class.getName()));
    }

    @Test
    public void passesConvertedConfigurationMapToMapOrientedComponent() throws Exception {
        MapOrientedComponentConfigurator configurator = new MapOrientedComponentConfigurator();
        RecordingMapOrientedComponent component = new RecordingMapOrientedComponent();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        addChild(configuration, "host", "localhost");
        addChild(configuration, "port", "8080");

        configurator.configureComponent(component, configuration, testRealm());

        assertEquals("localhost", component.configuration.get("host"));
        assertEquals("8080", component.configuration.get("port"));
    }

    private static void clearCompilerGeneratedClassCache() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
            MapOrientedComponentConfigurator.class,
            MethodHandles.lookup()
        );
        VarHandle classCache = lookup.findStaticVarHandle(
            MapOrientedComponentConfigurator.class,
            "class$org$codehaus$plexus$component$MapOrientedComponent",
            Class.class
        );
        classCache.set(null);
    }

    private static Object nonMapOrientedComponentSelectedAtRuntime() {
        Object[] candidates = new Object[] { new Object(), "not map oriented" };
        return candidates[(int) (System.nanoTime() & 1L)];
    }

    private static ClassRealm testRealm() throws Exception {
        ClassWorld classWorld = new ClassWorld();
        return classWorld.newRealm(
            "map-oriented-component-configurator-test",
            MapOrientedComponentConfiguratorTest.class.getClassLoader()
        );
    }

    private static void addChild(XmlPlexusConfiguration parent, String name, String value) {
        XmlPlexusConfiguration child = new XmlPlexusConfiguration(name);
        child.setValue(value);
        parent.addChild(child);
    }

    private static final class RecordingMapOrientedComponent implements MapOrientedComponent {
        private Map configuration;

        @Override
        public void addComponentRequirement(ComponentRequirement requirementDescriptor, Object requirementValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setComponentConfiguration(Map componentConfiguration) {
            configuration = componentConfiguration;
        }
    }
}
