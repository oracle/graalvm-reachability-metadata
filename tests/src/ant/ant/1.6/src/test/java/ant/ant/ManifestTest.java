/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.taskdefs.Manifest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManifestTest {
    @Test
    void loadsDefaultManifestResource() {
        Manifest manifest = Manifest.getDefaultManifest();

        assertThat(manifest.getManifestVersion()).isEqualTo(Manifest.DEFAULT_MANIFEST_VERSION);
        assertThat(manifest.getMainSection().getAttributeValue("Created-By"))
                .isEqualTo(System.getProperty("java.vm.version") + " ("
                        + System.getProperty("java.vm.vendor") + ")");
    }
}
