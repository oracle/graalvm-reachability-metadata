/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractComponentManagerTest {
    @Test
    public void looksUpSingletonCreatedByLegacyComponentFactory() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer("legacy-factory", null);
        try {
            ComponentDescriptor descriptor = new ComponentDescriptor();
            descriptor.setRole(String.class.getName());
            descriptor.setRoleHint("notification");
            descriptor.setImplementation("org_codehaus_plexus/plexus_container_default/notification.bsh");
            descriptor.setComponentFactory("bsh");
            descriptor.setInstantiationStrategy("singleton");
            container.addComponentDescriptor(descriptor);

            Object first = container.lookup(String.class.getName(), "notification");
            Object second = container.lookup(String.class.getName(), "notification");

            assertEquals("notification-42", first);
            assertSame(first, second);

            container.release(second);
            container.release(first);
        } finally {
            container.dispose();
        }
    }
}
