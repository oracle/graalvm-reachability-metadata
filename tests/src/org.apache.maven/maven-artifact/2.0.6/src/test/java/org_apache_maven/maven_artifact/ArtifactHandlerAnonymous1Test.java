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
}
