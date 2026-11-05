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
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the Plexus logging subsystem for a container-managed application service.
public class PlexusLoggingComponentTest {
    @Test
    public void enablesApplicationLoggerDuringComponentStartup() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            ComponentDescriptor descriptor = new ComponentDescriptor();
            descriptor.setRole(DeploymentReporter.class.getName());
            descriptor.setRoleHint("deployment");
            descriptor.setImplementation(LoggingDeploymentReporter.class.getName());
            descriptor.setInstantiationStrategy("singleton");
            descriptor.setLifecycleHandler("plexus");
            container.addComponentDescriptor(descriptor);

            DeploymentReporter reporter = (DeploymentReporter) container.lookup(
                    DeploymentReporter.class.getName(), "deployment");

            assertTrue(reporter.hasLogger());
            assertEquals(
                    "org_codehaus_plexus.plexus_container_default.PlexusLoggingComponentTest$DeploymentReporter:"
                            + "deployment",
                    reporter.loggerName());
            assertEquals(
                    "org_codehaus_plexus.plexus_container_default.PlexusLoggingComponentTest$DeploymentReporter:"
                            + "deployment",
                    reporter.childLoggerName());
        } finally {
            container.dispose();
        }
    }

    public interface DeploymentReporter {
        boolean hasLogger();

        String loggerName();

        String childLoggerName();
    }

    public static final class LoggingDeploymentReporter extends AbstractLogEnabled
            implements DeploymentReporter {
        @Override
        public boolean hasLogger() {
            return getLogger() != null && getLogger().isInfoEnabled();
        }

        @Override
        public String loggerName() {
            return getLogger().getName();
        }

        @Override
        public String childLoggerName() {
            return getLogger().getChildLogger("events").getName();
        }
    }
}
