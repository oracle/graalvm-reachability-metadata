/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.taskdefs.Manifest;
import org.junit.jupiter.api.Test;

public class ManifestTest {
    @Test
    void loadsAndAugmentsDefaultManifestResource() {
        Manifest manifest = Manifest.getDefaultManifest();

        String createdBy = System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ")";

        assertThat(manifest.getManifestVersion()).isEqualTo(Manifest.DEFAULT_MANIFEST_VERSION);
        assertThat(manifest.getMainSection().getAttributeValue("Ant-Version")).startsWith("Apache Ant");
        assertThat(manifest.getMainSection().getAttributeValue("Created-By")).isEqualTo(createdBy);
        assertThat(manifest.toString())
            .contains("Manifest-Version: " + Manifest.DEFAULT_MANIFEST_VERSION)
            .contains("Ant-Version: Apache Ant")
            .contains("Created-By: " + System.getProperty("java.vm.version"));
    }
}
