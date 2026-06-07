/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.component.MapOrientedComponent;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.MapOrientedComponentConfigurator;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MapOrientedComponentConfiguratorTest {
    @Test
    @Order(1)
    public void rejectsComponentsThatDoNotAcceptMapConfiguration() throws Exception {
        ComponentConfigurator configurator = new MapOrientedComponentConfigurator();
        Object component = nonMapOrientedComponentSelectedAtRuntime();
        ClassRealm realm = testRealm("map-oriented-component-configurator-rejection-test");
        clearCompilerGeneratedClassCache();
        assertNull(cachedMapOrientedComponentType());

        ComponentConfigurationException exception = assertThrows(
            ComponentConfigurationException.class,
            () -> configurator.configureComponent(component, new XmlPlexusConfiguration("configuration"), realm)
        );

        assertTrue(exception.getMessage().contains("can only process implementations"));
        assertTrue(exception.getMessage().contains(MapOrientedComponent.class.getName()));
        assertEquals(MapOrientedComponent.class, cachedMapOrientedComponentType());
    }

    @Test
    @Order(2)
    public void rejectsPlainComponentWithConfiguratorResolvedFromContainer() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            ComponentConfigurator configurator = (ComponentConfigurator) container.lookup(
                ComponentConfigurator.ROLE,
                "map-oriented"
            );
            clearCompilerGeneratedClassCache();
            assertNull(cachedMapOrientedComponentType());

            ComponentConfigurationException exception = assertThrows(
                ComponentConfigurationException.class,
                () -> configurator.configureComponent(
                    new PlainComponent(),
                    new XmlPlexusConfiguration("configuration"),
                    container.getContainerRealm()
                )
            );

            assertTrue(exception.getMessage().contains("can only process implementations"));
            assertEquals(MapOrientedComponent.class, cachedMapOrientedComponentType());
        } finally {
            container.dispose();
        }
    }

    @Test
    @Order(3)
    public void passesConvertedConfigurationMapToMapOrientedComponent() throws Exception {
        MapOrientedComponentConfigurator configurator = new MapOrientedComponentConfigurator();
        RecordingMapOrientedComponent component = new RecordingMapOrientedComponent();
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        addChild(configuration, "host", "localhost");
        addChild(configuration, "port", "8080");

        configurator.configureComponent(
            component,
            configuration,
            testRealm("map-oriented-component-configurator-conversion-test")
        );

        assertEquals("localhost", component.configuration.get("host"));
        assertEquals("8080", component.configuration.get("port"));
    }

    private static void clearCompilerGeneratedClassCache() throws Exception {
        classCacheHandle().set(null);
    }

    private static Class<?> cachedMapOrientedComponentType() throws Exception {
        return (Class<?>) classCacheHandle().get();
    }

    private static VarHandle classCacheHandle() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
            MapOrientedComponentConfigurator.class,
            MethodHandles.lookup()
        );
        return lookup.findStaticVarHandle(
            MapOrientedComponentConfigurator.class,
            "class$org$codehaus$plexus$component$MapOrientedComponent",
            Class.class
        );
    }

    private static Object nonMapOrientedComponentSelectedAtRuntime() {
        Object[] candidates = new Object[] {new Object(), "not map oriented" };
        return candidates[(int) (System.nanoTime() & 1L)];
    }

    private static ClassRealm testRealm(String id) throws Exception {
        ClassWorld classWorld = new ClassWorld();
        return classWorld.newRealm(id, MapOrientedComponentConfiguratorTest.class.getClassLoader());
    }

    private static void addChild(XmlPlexusConfiguration parent, String name, String value) {
        XmlPlexusConfiguration child = new XmlPlexusConfiguration(name);
        child.setValue(value);
        parent.addChild(child);
    }

    private static final class PlainComponent {
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
