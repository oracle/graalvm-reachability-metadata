/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;

import org.apache.tools.ant.taskdefs.Manifest;
import org.junit.jupiter.api.Test;

public class ManifestTest {
    @Test
    void readsDefaultManifestAndMergesApplicationAttributes() throws Exception {
        Manifest manifest = Manifest.getDefaultManifest();
        assertNotNull(manifest.getManifestVersion());
        manifest.addConfiguredAttribute(new Manifest.Attribute("Main-Class", "example.Application"));
        Manifest parsed = new Manifest(new StringReader(manifest.toString()));
        assertEquals("example.Application", parsed.getMainSection().getAttributeValue("Main-Class"));
        assertEquals(manifest.getManifestVersion(), parsed.getManifestVersion());
    }
}
