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
import org.codehaus.plexus.component.MapOrientedComponent;
import org.codehaus.plexus.component.composition.CompositionException;
import org.codehaus.plexus.component.composition.MapOrientedComponentComposer;
import org.codehaus.plexus.component.composition.UndefinedComponentComposerException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.discovery.ComponentDiscoveryListener;
import org.codehaus.plexus.component.factory.ComponentInstantiationException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapOrientedComponentComposerTest {
    @Test
    public void rejectsComponentsThatAreNotMapOriented() {
        MapOrientedComponentComposer composer = new MapOrientedComponentComposer();
        Object component = nonMapOrientedComponentSelectedAtRuntime();

        CompositionException exception = assertThrows(
            CompositionException.class,
            () -> composer.assembleComponent(component, null, null)
        );

        assertTrue(exception.getMessage().contains(component.getClass().getName()));
        assertTrue(exception.getMessage().contains(MapOrientedComponent.class.getName()));
    }

    @Test
    public void addsMappedRequirementsToMapOrientedComponents() throws Exception {
        MapOrientedComponentComposer composer = new MapOrientedComponentComposer();
        RecordingMapOrientedComponent component = new RecordingMapOrientedComponent();
        RecordingPlexusContainer container = new RecordingPlexusContainer();
        ComponentDescriptor descriptor = descriptor(RecordingMapOrientedComponent.class.getName());

        Object hintedDependency = new Object();
        Object singleDependency = new Object();
        Object fallbackDependency = new Object();
        Map<String, Object> mappedDependencies = new HashMap<>();
        mappedDependencies.put("mapped", new Object());
        List<Object> listedDependencies = new ArrayList<>();
        listedDependencies.add(new Object());
        listedDependencies.add(new Object());

        container.addComponent("hintedRolehint", hintedDependency);
        container.addComponent("singleRole", singleDependency);
        container.addComponent("fallbackRole", fallbackDependency);
        container.addMap("mapRole", mappedDependencies);
        container.addList("setRole", listedDependencies);

        ComponentRequirement hinted = requirement("hintedRole", null);
        hinted.setRoleHint("hint");
        ComponentRequirement single = requirement("singleRole", "single");
        ComponentRequirement mapped = requirement("mapRole", "map");
        ComponentRequirement set = requirement("setRole", "set");
        ComponentRequirement fallback = requirement("fallbackRole", "unexpected");

        descriptor.addRequirement(hinted);
        descriptor.addRequirement(single);
        descriptor.addRequirement(mapped);
        descriptor.addRequirement(set);
        descriptor.addRequirement(fallback);

        List assignedDescriptors = composer.assembleComponent(component, descriptor, container);

        assertEquals(5, assignedDescriptors.size());
        assertSame(hintedDependency, component.valueFor(hinted));
        assertSame(singleDependency, component.valueFor(single));
        assertSame(mappedDependencies, component.valueFor(mapped));
        assertSame(fallbackDependency, component.valueFor(fallback));
        assertEquals(new HashSet<>(listedDependencies), component.valueFor(set));
    }

    private static Object nonMapOrientedComponentSelectedAtRuntime() {
        Object[] candidates = new Object[] {new Object(), "not a map-oriented component" };
        return candidates[(int) (System.nanoTime() & 1L)];
    }

    private static ComponentRequirement requirement(String role, String mappingType) {
        ComponentRequirement requirement = new ComponentRequirement();
        requirement.setRole(role);
        requirement.setFieldMappingType(mappingType);
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

    private static final class RecordingMapOrientedComponent implements MapOrientedComponent {
        private final Map<ComponentRequirement, Object> values = new HashMap<>();

        @Override
        public void addComponentRequirement(ComponentRequirement requirementDescriptor, Object requirementValue)
            throws ComponentConfigurationException {
            values.put(requirementDescriptor, requirementValue);
        }

        @Override
        public void setComponentConfiguration(Map componentConfiguration) throws ComponentConfigurationException {
        }

        private Object valueFor(ComponentRequirement requirement) {
            return values.get(requirement);
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

        private void addMap(String role, Map dependencies) {
            maps.put(role, dependencies);
            descriptors.put(role, descriptor(role));
        }

        private void addList(String role, List dependencies) {
            lists.put(role, dependencies);
            descriptors.put(role, descriptor(role));
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
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void start() throws PlexusContainerException {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public void dispose() {
        }

        @Override
        public Context getContext() {
            return null;
        }

        @Override
        public void setParentPlexusContainer(PlexusContainer parentContainer) {
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
        public Object createComponentInstance(ComponentDescriptor componentDescriptor)
            throws ComponentInstantiationException, ComponentLifecycleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void composeComponent(Object component, ComponentDescriptor componentDescriptor)
            throws CompositionException, UndefinedComponentComposerException {
            throw new UnsupportedOperationException();
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
        public ClassRealm getComponentRealm(String componentKey) {
            return null;
        }

        @Override
        public void setLoggerManager(LoggerManager loggerManager) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoggerManager getLoggerManager() {
            return null;
        }
    }
}
