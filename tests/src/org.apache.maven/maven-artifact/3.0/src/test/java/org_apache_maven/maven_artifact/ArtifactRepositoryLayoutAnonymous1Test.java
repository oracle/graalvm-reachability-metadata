/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_artifact;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactRepositoryLayoutAnonymous1Test {
    @Test
    void roleInitializesArtifactRepositoryLayoutInterface() {
        assertThat(ArtifactRepositoryLayout.ROLE).isEqualTo(ArtifactRepositoryLayout.class.getName());
    }

    @Test
    void repositoryLayoutBuildsArtifactAndMetadataPaths() {
        ArtifactRepositoryLayout layout = new ArtifactRepositoryLayout() {
            @Override
            public String getId() {
                return "default";
            }

            @Override
            public String pathOf(Artifact artifact) {
                return "org/example/demo/1.0/demo-1.0.jar";
            }

            @Override
            public String pathOfLocalRepositoryMetadata(ArtifactMetadata metadata, ArtifactRepository repository) {
                return "org/example/demo/maven-metadata-local.xml";
            }

            @Override
            public String pathOfRemoteRepositoryMetadata(ArtifactMetadata metadata) {
                return "org/example/demo/maven-metadata.xml";
            }
        };

        assertThat(layout.getId()).isEqualTo("default");
        assertThat(layout.pathOf(null)).isEqualTo("org/example/demo/1.0/demo-1.0.jar");
        assertThat(layout.pathOfLocalRepositoryMetadata(null, null))
                .isEqualTo("org/example/demo/maven-metadata-local.xml");
        assertThat(layout.pathOfRemoteRepositoryMetadata(null)).isEqualTo("org/example/demo/maven-metadata.xml");
    }
}
