/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_toolchain;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.DefaultToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultToolchainManagerTest {
    @Test
    void storesToolchainInCurrentMavenProjectContext() {
        MavenProject project = new MavenProject();
        MavenSession session = new MavenSession(null, new DefaultMavenExecutionRequest(),
                new DefaultMavenExecutionResult(), project);
        session.setCurrentProject(project);
        ToolchainModel model = new ToolchainModel();
        model.setType("jdk");
        DefaultJavaToolChain toolchain = new DefaultJavaToolChain(model, null);

        new DefaultToolchainManager().storeToolchainToBuildContext(toolchain, session);

        assertThat(session.getPluginContext(toolchainsPlugin(), project))
                .containsEntry(DefaultToolchainManager.getStorageKey("jdk"), model);
    }

    private PluginDescriptor toolchainsPlugin() {
        PluginDescriptor plugin = new PluginDescriptor();
        plugin.setGroupId(PluginDescriptor.getDefaultPluginGroupId());
        plugin.setArtifactId(PluginDescriptor.getDefaultPluginArtifactId("toolchains"));
        return plugin;
    }
}
