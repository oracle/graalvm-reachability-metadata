/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.ArtifactTypeUtil;
import org.junit.jupiter.api.Test;

public class ArtifactTypeUtilTest {
    @Test
    void classifiesSupportedArtifactTypes() {
        assertThat(ArtifactTypeUtil.isJar("jar")).isTrue();
        assertThat(ArtifactTypeUtil.isJar("fast-jar")).isFalse();
        assertThat(ArtifactTypeUtil.isNativeBinary("native")).isTrue();
        assertThat(ArtifactTypeUtil.isNativeBinary("native-container")).isFalse();
        assertThat(ArtifactTypeUtil.isContainer("jar-container")).isTrue();
        assertThat(ArtifactTypeUtil.isContainer("native-container")).isTrue();
        assertThat(ArtifactTypeUtil.isContainer("jar")).isFalse();
    }
}
