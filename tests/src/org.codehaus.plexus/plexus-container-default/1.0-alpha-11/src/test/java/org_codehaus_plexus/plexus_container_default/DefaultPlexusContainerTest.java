/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPlexusContainerTest {
    @Test
    void constructorInitializesContainerContextAndLogger() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();

        try {
            assertThat(container.getContext().get(PlexusConstants.PLEXUS_KEY)).isSameAs(container);
            assertThat(container.getLogger()).isNotNull();
            assertThat(container.getLogger().getName()).contains(PlexusContainer.class.getName());
        } finally {
            container.dispose();
        }
    }

    @Test
    void createAndAutowireInstantiatesClassFromContainerRealm() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();

        try {
            Object component = container.createAndAutowire(DefaultPlexusContainerTest.class.getName());

            assertThat(component).isInstanceOf(DefaultPlexusContainerTest.class);
            assertThat(component).isNotSameAs(this);
        } finally {
            container.dispose();
        }
    }
}
