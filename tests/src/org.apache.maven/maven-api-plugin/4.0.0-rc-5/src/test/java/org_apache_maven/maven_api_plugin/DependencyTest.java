/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import org.apache.maven.api.plugin.descriptor.Dependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyTest {
    @Test
    void buildsRuntimeDependencyAndDerivesAlternateArtifact() {
        Dependency dependency = Dependency.newBuilder()
                .groupId("example.tools").artifactId("compiler").version("example-release").build();
        Dependency alternate = dependency.withArtifactId("compiler-api").withType("zip");
        Dependency relocated = dependency.with().groupId("example.runtime").build();

        assertThat(dependency.getGroupId()).isEqualTo("example.tools");
        assertThat(dependency.getArtifactId()).isEqualTo("compiler");
        assertThat(dependency.getVersion()).isEqualTo("example-release");
        assertThat(dependency.getType()).isEqualTo("jar");
        assertThat(alternate.getGroupId()).isEqualTo(dependency.getGroupId());
        assertThat(alternate.getArtifactId()).isEqualTo("compiler-api");
        assertThat(alternate.getVersion()).isEqualTo(dependency.getVersion());
        assertThat(alternate.getType()).isEqualTo("zip");
        assertThat(relocated.getGroupId()).isEqualTo("example.runtime");
        assertThat(relocated.getArtifactId()).isEqualTo("compiler");
        assertThat(relocated.getType()).isEqualTo("jar");
    }
}
