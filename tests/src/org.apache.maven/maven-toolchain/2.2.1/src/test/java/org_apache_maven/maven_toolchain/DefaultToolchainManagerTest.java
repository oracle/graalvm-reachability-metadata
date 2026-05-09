/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_toolchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.DefaultToolchainManager;
import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.junit.jupiter.api.Test;

public class DefaultToolchainManagerTest {
    @Test
    void storeToolchainUsesTheCurrentProjectPluginContext() throws Exception {
        MavenProject project = new MavenProject();
        project.setGroupId("example.group");
        project.setArtifactId("example-artifact");
        project.setVersion("1");

        ReactorManager reactorManager = new ReactorManager(Collections.singletonList(project));
        MavenSession session = new MavenSession(
                null,
                null,
                null,
                null,
                reactorManager,
                Collections.emptyList(),
                System.getProperty("user.dir"),
                new Properties(),
                new Properties(),
                new Date());
        session.setCurrentProject(project);

        ToolchainModel model = new ToolchainModel();
        model.setType("jdk");
        ToolchainPrivate toolchain = new StubToolchain(model);

        new DefaultToolchainManager().storeToolchainToBuildContext(toolchain, session);

        Map pluginContext = session.getPluginContext(toolchainsPluginDescriptor(), project);
        assertThat(pluginContext).containsEntry(DefaultToolchainManager.getStorageKey("jdk"), model);
    }

    private static PluginDescriptor toolchainsPluginDescriptor() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setGroupId(PluginDescriptor.getDefaultPluginGroupId());
        descriptor.setArtifactId(PluginDescriptor.getDefaultPluginArtifactId("toolchains"));
        return descriptor;
    }

    private static final class StubToolchain implements ToolchainPrivate {
        private final ToolchainModel model;

        private StubToolchain(ToolchainModel model) {
            this.model = model;
        }

        @Override
        public String getType() {
            return model.getType();
        }

        @Override
        public String findTool(String toolName) {
            return null;
        }

        @Override
        public boolean matchesRequirements(Map requirements) {
            return requirements.isEmpty();
        }

        @Override
        public ToolchainModel getModel() {
            return model;
        }
    }
}
