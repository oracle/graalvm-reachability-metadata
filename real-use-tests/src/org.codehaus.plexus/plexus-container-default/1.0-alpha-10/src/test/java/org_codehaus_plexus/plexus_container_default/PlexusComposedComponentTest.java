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
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Plexus component composition and dependency injection for a container-managed application service.
public class PlexusComposedComponentTest {
    @Test
    public void injectsNamedFormatterIntoApplicationService() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    Formatter.class, UppercaseFormatter.class, "uppercase"));

            ComponentDescriptor reporterDescriptor = descriptor(
                    ReleaseReporter.class, ReleaseReporterImpl.class, "release");
            ComponentRequirement formatterRequirement = new ComponentRequirement();
            formatterRequirement.setRole(Formatter.class.getName());
            formatterRequirement.setRoleHint("uppercase");
            formatterRequirement.setFieldName("formatter");
            reporterDescriptor.addRequirement(formatterRequirement);
            container.addComponentDescriptor(reporterDescriptor);

            ReleaseReporter reporter = (ReleaseReporter) container.lookup(
                    ReleaseReporter.class.getName(), "release");

            assertEquals("Release: PLEXUS CONTAINER", reporter.describe("plexus container"));
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

    public interface Formatter {
        String format(String value);
    }

    public static final class UppercaseFormatter implements Formatter {
        @Override
        public String format(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public interface ReleaseReporter {
        String describe(String productName);
    }

    public static final class ReleaseReporterImpl implements ReleaseReporter {
        private Formatter formatter;

        @Override
        public String describe(String productName) {
            return "Release: " + formatter.format(productName);
        }
    }
}
