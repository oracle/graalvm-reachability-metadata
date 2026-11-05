/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.LoggerManager;

import java.util.List;
import java.util.Map;

/// Adapts realm-aware lookups to the recording containers' single component registry.
abstract class PlexusContainerAdapter implements PlexusContainer {
    @Override
    public Object lookup(String role, ClassRealm realm) throws ComponentLookupException {
        return lookup(role);
    }

    @Override
    public Object lookup(String role, String hint, ClassRealm realm) throws ComponentLookupException {
        return lookup(role, hint);
    }

    @Override
    public Object lookup(Class role) throws ComponentLookupException {
        return lookup(role.getName());
    }

    @Override
    public Object lookup(Class role, ClassRealm realm) throws ComponentLookupException {
        return lookup(role.getName(), realm);
    }

    @Override
    public Object lookup(Class role, String hint) throws ComponentLookupException {
        return lookup(role.getName(), hint);
    }

    @Override
    public Object lookup(Class role, String hint, ClassRealm realm) throws ComponentLookupException {
        return lookup(role.getName(), hint, realm);
    }

    @Override
    public List lookupList(String role, ClassRealm realm) throws ComponentLookupException {
        return lookupList(role);
    }

    @Override
    public List lookupList(Class role) throws ComponentLookupException {
        return lookupList(role.getName());
    }

    @Override
    public List lookupList(Class role, ClassRealm realm) throws ComponentLookupException {
        return lookupList(role.getName(), realm);
    }

    @Override
    public Map lookupMap(String role, ClassRealm realm) throws ComponentLookupException {
        return lookupMap(role);
    }

    @Override
    public Map lookupMap(Class role) throws ComponentLookupException {
        return lookupMap(role.getName());
    }

    @Override
    public Map lookupMap(Class role, ClassRealm realm) throws ComponentLookupException {
        return lookupMap(role.getName(), realm);
    }

    @Override
    public ComponentDescriptor getComponentDescriptor(String role, String hint) {
        return getComponentDescriptor("default".equals(hint) ? role : role + hint);
    }

    @Override
    public ComponentDescriptor getComponentDescriptor(String role, ClassRealm realm) {
        return getComponentDescriptor(role);
    }

    @Override
    public ComponentDescriptor getComponentDescriptor(String role, String hint, ClassRealm realm) {
        return getComponentDescriptor(role, hint);
    }

    @Override
    public Map getComponentDescriptorMap(String role, ClassRealm realm) {
        return getComponentDescriptorMap(role);
    }

    @Override
    public List getComponentDescriptorList(String role, ClassRealm realm) {
        return getComponentDescriptorList(role);
    }

    @Override
    public ClassRealm getLookupRealm() {
        return getContainerRealm();
    }

    @Override
    public ClassRealm getLookupRealm(Object component) {
        return getLookupRealm();
    }

    @Override
    public ClassRealm setLookupRealm(ClassRealm realm) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClassRealm getComponentRealm(String id) {
        return getContainerRealm();
    }

    @Override
    public ClassRealm createComponentRealm(String id, List jars) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setParentPlexusContainer(PlexusContainer parent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLoggerManager(LoggerManager manager) {
        throw new UnsupportedOperationException();
    }
}
