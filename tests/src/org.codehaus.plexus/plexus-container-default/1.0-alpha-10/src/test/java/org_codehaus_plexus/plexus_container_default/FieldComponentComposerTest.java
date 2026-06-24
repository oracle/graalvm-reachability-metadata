/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.composition.CompositionException;
import org.codehaus.plexus.component.composition.FieldComponentComposer;
import org.codehaus.plexus.component.discovery.ComponentDiscoveryListener;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.codehaus.plexus.configuration.PlexusConfigurationResourceException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.LoggerManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FieldComponentComposerTest {
    @Test
    public void assignsRequirementsToSupportedFieldShapes() throws Exception {
        FieldComponentComposer composer = new FieldComponentComposer();
        ComponentWithAllFieldKinds component = new ComponentWithAllFieldKinds();
        RecordingPlexusContainer container = new RecordingPlexusContainer();
        ComponentDescriptor componentDescriptor = descriptor(ComponentWithAllFieldKinds.class.getName());

        Object[] arrayDependency = new Object[] {"nested dependency" };
        List<String> listDependencies = new ArrayList<>();
        listDependencies.add("list dependency");
        Map<String, String> mapDependencies = new HashMap<>();
        mapDependencies.put("key", "map dependency");
        Dependency ordinaryDependency = new Dependency();

        String arrayRole = "arrayRole";
        String mapRole = "mapRole";
        String listRole = "listRole";
        String ordinaryRole = Dependency.class.getName();

        container.addList(arrayRole, listOf(arrayDependency));
        container.addList(listRole, listDependencies);
        container.addMap(mapRole, mapDependencies);
        container.addComponent(ordinaryRole, ordinaryDependency);

        componentDescriptor.addRequirement(requirement(arrayRole, "arrayDependencies"));
        componentDescriptor.addRequirement(requirement(mapRole, "mapDependencies"));
        componentDescriptor.addRequirement(requirement(listRole, "listDependencies"));
        componentDescriptor.addRequirement(requirement(ordinaryRole, "ordinaryDependency"));

        composer.assembleComponent(component, componentDescriptor, container);

        assertEquals(1, component.arrayDependencies.length);
        assertSame(arrayDependency, component.arrayDependencies[0]);
        assertSame(mapDependencies, component.mapDependencies);
        assertSame(listDependencies, component.listDependencies);
        assertSame(ordinaryDependency, component.ordinaryDependency);
    }

    @Test
    public void findsRequirementFieldByRoleUsingThreadContextClassLoader() throws Exception {
        ExposingFieldComponentComposer composer = new ExposingFieldComponentComposer();
        ComponentDescriptor descriptor = descriptor(DerivedComponent.class.getName());
        ComponentRequirement requirement = requirement(Dependency.class.getName(), null);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(FieldComponentComposerTest.class.getClassLoader());
        try {
            Field field = composer.findField(new DerivedComponent(), descriptor, requirement);

            assertEquals("inheritedDependency", field.getName());
            assertSame(BaseComponent.class, field.getDeclaringClass());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ComponentRequirement requirement(String role, String fieldName) {
        ComponentRequirement requirement = new ComponentRequirement();
        requirement.setRole(role);
        requirement.setFieldName(fieldName);
        return requirement;
    }

    private static ComponentDescriptor descriptor(String role) {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(role);
        descriptor.setImplementation(role);
        return descriptor;
    }

    private static List<Object> listOf(Object value) {
        List<Object> values = new ArrayList<>();
        values.add(value);
        return values;
    }

    public static final class Dependency {
    }

    public static class BaseComponent {
        private Dependency inheritedDependency;
    }

    public static final class DerivedComponent extends BaseComponent {
    }

    public static final class ComponentWithAllFieldKinds {
        private Object[] arrayDependencies;

        private Map mapDependencies;

        private List listDependencies;

        private Dependency ordinaryDependency;
    }

    private static final class ExposingFieldComponentComposer extends FieldComponentComposer {
        private Field findField(Object component, ComponentDescriptor descriptor, ComponentRequirement requirement)
            throws CompositionException {
            return findMatchingField(component, descriptor, requirement, null);
        }
    }

    private static final class RecordingPlexusContainer implements PlexusContainer {
        private final Map<String, Object> components = new HashMap<>();

        private final Map<String, List> lists = new HashMap<>();

        private final Map<String, Map> maps = new HashMap<>();

        private final Map<String, ComponentDescriptor> descriptors = new HashMap<>();

        private void addComponent(String key, Object component) {
            components.put(key, component);
            descriptors.put(key, descriptor(key));
        }

        private void addList(String role, List dependencies) {
            lists.put(role, dependencies);
            descriptors.put(role, descriptor(role));
        }

        private void addMap(String role, Map dependencies) {
            maps.put(role, dependencies);
            descriptors.put(role, descriptor(role));
        }

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public Date getCreationDate() {
            return new Date(0L);
        }

        @Override
        public Object lookup(String componentKey) throws ComponentLookupException {
            Object component = components.get(componentKey);
            if (component == null) {
                throw new ComponentLookupException("Missing component: " + componentKey);
            }
            return component;
        }

        @Override
        public Object lookup(String role, String roleHint) throws ComponentLookupException {
            return lookup(role + roleHint);
        }

        @Override
        public Map lookupMap(String role) throws ComponentLookupException {
            Map dependencies = maps.get(role);
            if (dependencies == null) {
                throw new ComponentLookupException("Missing map: " + role);
            }
            return dependencies;
        }

        @Override
        public List lookupList(String role) throws ComponentLookupException {
            List dependencies = lists.get(role);
            if (dependencies == null) {
                throw new ComponentLookupException("Missing list: " + role);
            }
            return dependencies;
        }

        @Override
        public ComponentDescriptor getComponentDescriptor(String componentKey) {
            return descriptors.get(componentKey);
        }

        @Override
        public Map getComponentDescriptorMap(String role) {
            Map<String, ComponentDescriptor> descriptorMap = new HashMap<>();
            descriptorMap.put(role, descriptors.get(role));
            return descriptorMap;
        }

        @Override
        public List getComponentDescriptorList(String role) {
            return listOf(descriptors.get(role));
        }

        @Override
        public boolean hasChildContainer(String name) {
            return false;
        }

        @Override
        public void removeChildContainer(String name) {
        }

        @Override
        public PlexusContainer getChildContainer(String name) {
            return null;
        }

        @Override
        public PlexusContainer createChildContainer(String name, List classpathJars, Map context)
            throws PlexusContainerException {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlexusContainer createChildContainer(
            String name,
            List classpathJars,
            Map context,
            List discoveryListeners
        ) throws PlexusContainerException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addComponentDescriptor(ComponentDescriptor componentDescriptor)
            throws ComponentRepositoryException {
            descriptors.put(componentDescriptor.getComponentKey(), componentDescriptor);
        }

        @Override
        public void release(Object component) throws ComponentLifecycleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseAll(Map components) throws ComponentLifecycleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseAll(List components) throws ComponentLifecycleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasComponent(String componentKey) {
            return components.containsKey(componentKey);
        }

        @Override
        public boolean hasComponent(String role, String roleHint) {
            return hasComponent(role + roleHint);
        }

        @Override
        public void suspend(Object component) throws ComponentLifecycleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resume(Object component) throws ComponentLifecycleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void initialize() throws PlexusContainerException {
        }

        @Override
        public void start() throws PlexusContainerException {
        }

        @Override
        public void dispose() {
        }

        @Override
        public Context getContext() {
            return null;
        }

        @Override
        public void addContextValue(Object key, Object value) {
        }

        @Override
        public void setConfigurationResource(Reader configuration) throws PlexusConfigurationResourceException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Logger getLogger() {
            return null;
        }

        @Override
        public void registerComponentDiscoveryListener(ComponentDiscoveryListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeComponentDiscoveryListener(ComponentDiscoveryListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addJarRepository(File repository) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addJarResource(File resource) throws PlexusContainerException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassRealm getContainerRealm() {
            return null;
        }

        @Override
        public Object autowire(Object component) throws CompositionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object createAndAutowire(String clazz)
            throws CompositionException, ClassNotFoundException, InstantiationException, IllegalAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReloadingEnabled(boolean reloadingEnabled) {
        }

        @Override
        public boolean isReloadingEnabled() {
            return false;
        }

        @Override
        public LoggerManager getLoggerManager() {
            return null;
        }
    }
}
