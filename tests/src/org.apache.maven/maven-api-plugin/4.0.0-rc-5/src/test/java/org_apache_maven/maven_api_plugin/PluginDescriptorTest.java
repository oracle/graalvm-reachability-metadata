/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.plugin.descriptor.MojoDescriptor;
import org.apache.maven.api.plugin.descriptor.PluginDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PluginDescriptorTest {
    @Test
    void buildsPluginIdentityAndSnapshotsItsGoals() {
        MojoDescriptor compile = MojoDescriptor.newBuilder().goal("compile").build();
        MojoDescriptor verify = MojoDescriptor.newBuilder().goal("verify").build();
        List<MojoDescriptor> mojos = new ArrayList<>(List.of(compile, verify));
        PluginDescriptor plugin = PluginDescriptor.newBuilder()
                .groupId("example.plugins")
                .artifactId("sample-plugin")
                .version("example-release")
                .name("Sample plugin")
                .description("Compiles and verifies a project")
                .goalPrefix("sample")
                .namespaceUri("urn:example:plugin")
                .mojos(mojos)
                .build();
        mojos.clear();

        assertThat(plugin.getPluginLookupKey()).isEqualTo("example.plugins:sample-plugin");
        assertThat(plugin.getId()).isEqualTo("example.plugins:sample-plugin:example-release");
        assertThat(plugin.getName()).isEqualTo("Sample plugin");
        assertThat(plugin.getDescription()).isEqualTo("Compiles and verifies a project");
        assertThat(plugin.getGoalPrefix()).isEqualTo("sample");
        assertThat(plugin.getNamespaceUri()).isEqualTo("urn:example:plugin");
        assertThat(plugin.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(plugin.isIsolatedRealm()).isFalse();
        assertThat(plugin.isInheritedByDefault()).isTrue();
        assertThat(plugin.getMojos()).containsExactly(compile, verify);
        assertThatThrownBy(() -> plugin.getMojos().add(compile))
                .isInstanceOf(UnsupportedOperationException.class);

        PluginDescriptor renamed = plugin.withArtifactId("renamed-plugin");
        PluginDescriptor standalone = plugin.with().isolatedRealm(true).inheritedByDefault(false).build();
        assertThat(renamed.getPluginLookupKey()).isEqualTo("example.plugins:renamed-plugin");
        assertThat(renamed.getVersion()).isEqualTo(plugin.getVersion());
        assertThat(renamed.getMojos()).containsExactly(compile, verify);
        assertThat(standalone.isIsolatedRealm()).isTrue();
        assertThat(standalone.isInheritedByDefault()).isFalse();
        assertThat(plugin.getArtifactId()).isEqualTo("sample-plugin");
        assertThat(plugin.isInheritedByDefault()).isTrue();
    }
}
