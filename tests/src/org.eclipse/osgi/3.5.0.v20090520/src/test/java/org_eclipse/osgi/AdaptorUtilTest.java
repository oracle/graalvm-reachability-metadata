/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.osgi.internal.baseadaptor.AdaptorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdaptorUtilTest {
    @Test
    public void encodesAndReadsAFileWithReservedCharacters(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("notes #1 + draft.txt");
        Files.writeString(file, "adapter payload");
        URL url = AdaptorUtil.encodeFileURL(file.toFile());
        assertEquals(file.toUri(), url.toURI());
        assertTrue(url.toExternalForm().contains("%23"));
        try (var input = url.openStream()) {
            assertEquals("adapter payload", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
