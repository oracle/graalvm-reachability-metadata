/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.nativ.JLineNativeLoader;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JLineNativeLoaderTest {

    @Test(timeout = 30000L)
    public void extractsAndLoadsPackagedNativeLibrary() throws Exception {
        Path extractionDirectory = Files.createTempDirectory("jline-native");
        String originalTemporaryDirectory = System.getProperty("jline.tmpdir");
        System.setProperty("jline.tmpdir", extractionDirectory.toString());

        try {
            assertTrue(JLineNativeLoader.initialize());

            Path nativeLibrary = Path.of(JLineNativeLoader.getNativeLibraryPath());
            assertTrue(Files.isRegularFile(nativeLibrary));
            assertTrue(Files.isReadable(nativeLibrary));
            assertEquals(extractionDirectory.toAbsolutePath(), nativeLibrary.getParent());

            String source = JLineNativeLoader.getNativeLibrarySourceUrl();
            assertNotNull(source);
            assertTrue(source.contains("org/jline/nativ/"));
            assertTrue(source.contains("jlinenative"));
        } finally {
            if (originalTemporaryDirectory == null) {
                System.clearProperty("jline.tmpdir");
            } else {
                System.setProperty("jline.tmpdir", originalTemporaryDirectory);
            }
        }
    }
}
