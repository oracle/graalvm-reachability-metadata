/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SimplePlexusContainerManagerTest {
    @Test
    public void defaultContainerExposesItselfThroughContext() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();

        try {
            assertThat(container.getName()).isEqualTo("default");
            assertThat(container.getContext().get(PlexusConstants.PLEXUS_KEY)).isSameAs(container);
            assertThat(container.getContainerRealm()).isNotNull();
        } finally {
            container.dispose();
        }
    }

    @Test
    public void constructorInitializesConfiguredContextAndCoreComponents() throws Exception {
        Map context = new HashMap();
        context.put("configured-key", "configured-value");
        DefaultPlexusContainer container = new DefaultPlexusContainer(
            new StringReader("<plexus><components/></plexus>"),
            context
        );

        try {
            ComponentConfigurator configurator = (ComponentConfigurator) container.lookup(
                ComponentConfigurator.ROLE,
                "basic"
            );

            assertThat(configurator).isNotNull();
            assertThat(container.getContext().get("configured-key")).isEqualTo("configured-value");
        } finally {
            container.dispose();
        }
    }
}
