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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Plexus list-based component lookup for an application pipeline assembled from named steps.
public class PlexusComponentListLookupTest {
    @Test
    public void looksUpAllPipelineStepsAsList() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    ReleaseStep.class, CompileStep.class, "compile"));
            container.addComponentDescriptor(descriptor(
                    ReleaseStep.class, PackageStep.class, "package"));

            List<?> steps = container.lookupList(ReleaseStep.class.getName());
            List<String> results = new ArrayList<>();
            for (Object step : steps) {
                results.add(((ReleaseStep) step).execute());
            }

            assertEquals(2, steps.size());
            assertTrue(results.contains("compiled application"));
            assertTrue(results.contains("packaged application"));
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

    public interface ReleaseStep {
        String execute();
    }

    public static final class CompileStep implements ReleaseStep {
        @Override
        public String execute() {
            return "compiled application";
        }
    }

    public static final class PackageStep implements ReleaseStep {
        @Override
        public String execute() {
            return "packaged application";
        }
    }
}
