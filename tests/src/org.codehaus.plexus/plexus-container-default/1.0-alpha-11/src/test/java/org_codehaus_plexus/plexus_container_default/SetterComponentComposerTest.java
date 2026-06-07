/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.ComponentLookupManager;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.composition.ComponentComposerManager;
import org.codehaus.plexus.component.composition.CompositionException;
import org.codehaus.plexus.component.composition.setter.SetterComponentComposer;
import org.codehaus.plexus.component.discovery.ComponentDiscovererManager;
import org.codehaus.plexus.component.discovery.ComponentDiscoveryListener;
import org.codehaus.plexus.component.factory.ComponentFactoryManager;
import org.codehaus.plexus.component.manager.ComponentManagerManager;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.ComponentRepository;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.configuration.PlexusConfigurationResourceException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.lifecycle.LifecycleHandlerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.LoggerManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SetterComponentComposerTest {
    @Test
    public void assignsArrayAndMapRequirementsThroughBeanSetters() throws Exception {
        SetterComponentComposer composer = new SetterComponentComposer();
        ComponentWithSetterDependencies component = new ComponentWithSetterDependencies();
        RecordingPlexusContainer container = new RecordingPlexusContainer();
        ComponentDescriptor componentDescriptor = descriptor(ComponentWithSetterDependencies.class.getName());

        String arrayRole = "arrayRole";
        String mapRole = "mapRole";
        List<Object> emptyArrayDependencies = new ArrayList<>();
        Map<String, String> mapDependencies = new HashMap<>();
        mapDependencies.put("key", "map dependency");

        container.addList(arrayRole, emptyArrayDependencies);
        container.addMap(mapRole, mapDependencies);

        componentDescriptor.addRequirement(requirement(arrayRole, "arrayDependencies"));
        componentDescriptor.addRequirement(requirement(mapRole, "mapDependencies"));

        composer.assembleComponent(component, componentDescriptor, container);

        assertEquals(0, component.arrayDependencies.length);
        assertSame(mapDependencies, component.mapDependencies);
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

    public static final class ComponentWithSetterDependencies {
        private Object[] arrayDependencies;

        private Map mapDependencies;

        public void setArrayDependencies(Object[] arrayDependencies) {
            this.arrayDependencies = arrayDependencies;
        }

        public void setMapDependencies(Map mapDependencies) {
            this.mapDependencies = mapDependencies;
        }
    }

    private static final class RecordingPlexusContainer implements PlexusContainer {
        private final Map<String, Object> components = new HashMap<>();

        private final Map<String, List> lists = new HashMap<>();

        private final Map<String, Map> maps = new HashMap<>();

        private final Map<String, ComponentDescriptor> descriptors = new HashMap<>();

        private void addMap(String role, Map dependencies) {
            maps.put(role, dependencies);
            descriptors.put(role, descriptor(role));
        }

        private void addList(String role, List dependencies) {
            lists.put(role, dependencies);
            descriptors.put(role, descriptor(role));
        }

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void setName(String name) {
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

        public Object lookup(Class componentClass) throws ComponentLookupException {
            return lookup(componentClass.getName());
        }

        public Object lookup(Class role, String roleHint) throws ComponentLookupException {
            return lookup(role.getName(), roleHint);
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

        public Map lookupMap(Class role) throws ComponentLookupException {
            return lookupMap(role.getName());
        }

        public List lookupList(Class role) throws ComponentLookupException {
            return lookupList(role.getName());
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

        public void initialize() throws PlexusContainerException {
        }

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

        public ClassWorld getClassWorld() {
            return null;
        }

        public List discoverComponents(ClassRealm classRealm)
            throws PlexusConfigurationException, ComponentRepositoryException {
            return new ArrayList();
        }

        @Override
        public void setParentPlexusContainer(PlexusContainer parentContainer) {
        }

        public PlexusContainer getParentContainer() {
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

        public ComponentRepository getComponentRepository() {
            return null;
        }

        public void setComponentRepository(ComponentRepository componentRepository) {
        }

        public LifecycleHandlerManager getLifecycleHandlerManager() {
            return null;
        }

        public void setLifecycleHandlerManager(LifecycleHandlerManager lifecycleHandlerManager) {
        }

        public ComponentManagerManager getComponentManagerManager() {
            return null;
        }

        public void setComponentManagerManager(ComponentManagerManager componentManagerManager) {
        }

        public ComponentDiscovererManager getComponentDiscovererManager() {
            return null;
        }

        public void setComponentDiscovererManager(ComponentDiscovererManager componentDiscovererManager) {
        }

        public ComponentFactoryManager getComponentFactoryManager() {
            return null;
        }

        public void setComponentFactoryManager(ComponentFactoryManager componentFactoryManager) {
        }

        public ComponentLookupManager getComponentLookupManager() {
            return null;
        }

        public void setComponentLookupManager(ComponentLookupManager componentLookupManager) {
        }

        public ComponentComposerManager getComponentComposerManager() {
            return null;
        }

        public void setComponentComposerManager(ComponentComposerManager componentComposerManager) {
        }

        public LoggerManager getLoggerManager() {
            return null;
        }

        public void setLoggerManager(LoggerManager loggerManager) {
        }

        public void setConfiguration(PlexusConfiguration configuration) {
        }

        public PlexusConfiguration getConfiguration() {
            return null;
        }
    }
}
