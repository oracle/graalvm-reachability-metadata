/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.Map;

import io.netty.util.Version;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionTest {
    @Test
    void identifiesCommonArtifactFromPackagedProperties() {
        Map<String, Version> versions = Version.identify();
        Version common = versions.get("netty-common");
        Assertions.assertNotNull(common);
        Assertions.assertEquals("netty-common", common.artifactId());
        Assertions.assertFalse(common.artifactVersion().isEmpty());
        Assertions.assertFalse(common.longCommitHash().isEmpty());
        Assertions.assertTrue(common.toString().startsWith("netty-common-"));
        Assertions.assertEquals(common.artifactVersion(),
                Version.identify(VersionTest.class.getClassLoader()).get("netty-common").artifactVersion());
    }
}
