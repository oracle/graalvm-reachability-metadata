/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.ComponentLookupManager;
import org.codehaus.plexus.DefaultComponentLookupManager;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SimplePlexusContainerManagerTest {
    @Test
    public void defaultLookupManagerReportsMissingComponentsFromContainerRepository() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        DefaultComponentLookupManager manager = new DefaultComponentLookupManager();
        manager.setContainer(container);

        try {
            assertThat(ComponentLookupManager.ROLE).isEqualTo(ComponentLookupManager.class.getName());
            assertThatThrownBy(() -> manager.lookup(MissingComponent.class.getName()))
                .isInstanceOf(ComponentLookupException.class)
                .hasMessageContaining(MissingComponent.class.getName());
        } finally {
            container.dispose();
        }
    }

    public static final class MissingComponent {
    }
}
