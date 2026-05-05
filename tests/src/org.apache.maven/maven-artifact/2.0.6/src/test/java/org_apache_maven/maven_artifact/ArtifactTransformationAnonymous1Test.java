/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_artifact;

import org.apache.maven.artifact.transform.ArtifactTransformation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactTransformationAnonymous1Test {
    @Test
    void roleInitializesArtifactTransformationInterface() {
        assertThat(ArtifactTransformation.ROLE).isEqualTo(ArtifactTransformation.class.getName());
    }
}
