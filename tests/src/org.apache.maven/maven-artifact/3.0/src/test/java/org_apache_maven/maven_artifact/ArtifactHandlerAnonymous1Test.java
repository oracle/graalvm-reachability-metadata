/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_artifact;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactHandlerAnonymous1Test {
    @Test
    void roleInitializesArtifactHandlerInterface() {
        assertThat(ArtifactHandler.ROLE).isEqualTo(ArtifactHandler.class.getName());
    }

    @Test
    void artifactHandlerExposesPackagingDetails() {
        ArtifactHandler handler = new ArtifactHandler() {
            @Override
            public String getExtension() {
                return "jar";
            }

            @Override
            public String getDirectory() {
                return "java";
            }

            @Override
            public String getClassifier() {
                return "sources";
            }

            @Override
            public String getPackaging() {
                return "jar";
            }

            @Override
            public boolean isIncludesDependencies() {
                return false;
            }

            @Override
            public String getLanguage() {
                return "java";
            }

            @Override
            public boolean isAddedToClasspath() {
                return true;
            }
        };

        assertThat(handler.getExtension()).isEqualTo("jar");
        assertThat(handler.getDirectory()).isEqualTo("java");
        assertThat(handler.getClassifier()).isEqualTo("sources");
        assertThat(handler.getPackaging()).isEqualTo("jar");
        assertThat(handler.isIncludesDependencies()).isFalse();
        assertThat(handler.getLanguage()).isEqualTo("java");
        assertThat(handler.isAddedToClasspath()).isTrue();
    }
}
