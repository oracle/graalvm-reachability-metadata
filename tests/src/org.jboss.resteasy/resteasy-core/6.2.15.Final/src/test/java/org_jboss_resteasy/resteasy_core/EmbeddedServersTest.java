/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.embedded.EmbeddedServers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddedServersTest {
    @Test
    void resolvesContextFromConfiguredApplicationClassName() {
        ResteasyDeploymentImpl deployment = new ResteasyDeploymentImpl();
        deployment.setApplicationClass(ApiApplication.class.getName());
        assertThat(EmbeddedServers.resolveContext(deployment)).isEqualTo("service");
        assertThat(EmbeddedServers.checkContextPath(deployment)).isEqualTo("/service");
    }

    @ApplicationPath("service")
    public static class ApiApplication extends Application {
    }
}
