/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_component_api;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlexusContainerAnonymous1Test {
    @Test
    void generatesDefaultContainerComponentKey() {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(PlexusContainer.ROLE);

        assertThat(descriptor.getComponentKey()).isEqualTo("org.codehaus.plexus.PlexusContainer");
    }

    @Test
    void distinguishesHintedContainerComponents() {
        ComponentDescriptor parent = new ComponentDescriptor();
        parent.setRole(PlexusContainer.ROLE);
        parent.setRoleHint("parent");
        ComponentDescriptor child = new ComponentDescriptor();
        child.setRole(PlexusContainer.ROLE);
        child.setRoleHint("child");

        assertThat(parent.getComponentKey()).isEqualTo("org.codehaus.plexus.PlexusContainerparent");
        assertThat(child.getComponentKey()).isEqualTo("org.codehaus.plexus.PlexusContainerchild");
        assertThat(parent).isNotEqualTo(child);

        child.setRoleHint("parent");
        assertThat(child).isEqualTo(parent);
        assertThat(child.hashCode()).isEqualTo(parent.hashCode());
    }
}
