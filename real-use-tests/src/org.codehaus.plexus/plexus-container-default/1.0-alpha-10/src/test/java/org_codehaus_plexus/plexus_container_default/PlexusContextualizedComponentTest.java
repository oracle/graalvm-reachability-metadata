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
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Plexus context injection for a container-managed application component.
public class PlexusContextualizedComponentTest {
    @Test
    public void injectsApplicationContextIntoComponent() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addContextValue("workspace", "release-tools");

            ComponentDescriptor descriptor = new ComponentDescriptor();
            descriptor.setRole(WorkspaceBanner.class.getName());
            descriptor.setRoleHint("console");
            descriptor.setImplementation(WorkspaceBannerImpl.class.getName());
            descriptor.setInstantiationStrategy("singleton");
            descriptor.setLifecycleHandler("plexus");
            container.addComponentDescriptor(descriptor);

            WorkspaceBanner banner = (WorkspaceBanner) container.lookup(
                    WorkspaceBanner.class.getName(), "console");

            assertEquals("release-tools: deployment ready", banner.render("deployment ready"));
        } finally {
            container.dispose();
        }
    }

    public interface WorkspaceBanner {
        String render(String message);
    }

    public static final class WorkspaceBannerImpl implements WorkspaceBanner, Contextualizable {
        private String workspace;

        @Override
        public void contextualize(Context context) throws ContextException {
            workspace = (String) context.get("workspace");
        }

        @Override
        public String render(String message) {
            return workspace + ": " + message;
        }
    }
}
