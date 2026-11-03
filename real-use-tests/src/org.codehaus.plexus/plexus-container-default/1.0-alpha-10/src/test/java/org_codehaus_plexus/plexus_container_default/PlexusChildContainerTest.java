/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the Plexus child-container subsystem for an application that isolates a tenant plugin.
public class PlexusChildContainerTest {
    @Test
    public void isolatesTenantPluginWhileSharingHostService() throws Exception {
        DefaultPlexusContainer host = new DefaultPlexusContainer();
        host.addComponentDescriptor(descriptor(HostClock.class, HostClockImpl.class, "host"));

        Map<Object, Object> context = new HashMap<>();
        context.put("tenant", "acme");
        PlexusContainer tenant = host.createChildContainer(
                "acme", Collections.emptyList(), context);
        tenant.addComponentDescriptor(descriptor(
                TenantPlugin.class, TenantPluginImpl.class, "plugin"));

        try {
            HostClock hostClock = (HostClock) tenant.lookup(
                    HostClock.class.getName(), "host");
            TenantPlugin plugin = (TenantPlugin) tenant.lookup(
                    TenantPlugin.class.getName(), "plugin");

            assertTrue(host.hasChildContainer("acme"));
            assertEquals("acme", tenant.getName());
            assertSame(hostClock, host.lookup(HostClock.class.getName(), "host"));
            assertEquals("acme:2025-01-01", plugin.releaseDate("2025-01-01"));
            assertFalse(host.hasComponent(TenantPlugin.class.getName(), "plugin"));
        } finally {
            tenant.dispose();
            host.removeChildContainer("acme");
            host.dispose();
        }

        assertFalse(host.hasChildContainer("acme"));
    }

    private static ComponentDescriptor descriptor(
            Class<?> role, Class<?> implementation, String roleHint) {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(role.getName());
        descriptor.setRoleHint(roleHint);
        descriptor.setImplementation(implementation.getName());
        descriptor.setInstantiationStrategy("singleton");
        return descriptor;
    }

    public interface HostClock {
        String today();
    }

    public static final class HostClockImpl implements HostClock {
        @Override
        public String today() {
            return "2025-01-01";
        }
    }

    public interface TenantPlugin {
        String releaseDate(String date);
    }

    public static final class TenantPluginImpl implements TenantPlugin {
        @Override
        public String releaseDate(String date) {
            return "acme:" + date;
        }
    }
}
