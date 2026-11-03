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

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Plexus map lookup so an application can select named plugin implementations.
public class PlexusComponentMapLookupTest {
    @Test
    public void looksUpNamedApplicationPluginsAsMap() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    BuildStatusFormatter.class, PlainBuildStatusFormatter.class, "plain"));
            container.addComponentDescriptor(descriptor(
                    BuildStatusFormatter.class, DetailedBuildStatusFormatter.class, "detailed"));

            Map<?, ?> formatters = container.lookupMap(BuildStatusFormatter.class.getName());

            assertEquals(2, formatters.size());
            assertTrue(formatters.containsKey("plain"));
            assertTrue(formatters.containsKey("detailed"));
            BuildStatusFormatter plain = (BuildStatusFormatter) formatters.get("plain");
            BuildStatusFormatter detailed = (BuildStatusFormatter) formatters.get("detailed");
            assertNotNull(plain);
            assertNotNull(detailed);
            assertEquals("build passed", plain.format("passed"));
            assertEquals("BUILD STATUS: PASSED", detailed.format("passed"));
        } finally {
            container.dispose();
        }
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

    public interface BuildStatusFormatter {
        String format(String status);
    }

    public static final class PlainBuildStatusFormatter implements BuildStatusFormatter {
        @Override
        public String format(String status) {
            return "build " + status;
        }
    }

    public static final class DetailedBuildStatusFormatter implements BuildStatusFormatter {
        @Override
        public String format(String status) {
            return "BUILD STATUS: " + status.toUpperCase(Locale.ROOT);
        }
    }
}
