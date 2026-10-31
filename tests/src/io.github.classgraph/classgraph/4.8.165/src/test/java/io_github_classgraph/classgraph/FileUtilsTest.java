/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.CloseableByteBuffer;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FileUtilsTest {
    @TempDir
    Path directory;

    @Test
    void readsAndReleasesMemoryMappedJar() throws Exception {
        Path archive = ScanSupport.archive(directory);
        try (ScanResult scan = ScanSupport.graph(archive).enableMemoryMapping().scan(2)) {
            Resource resource = scan.getResourcesWithPath(ScanSupport.PAYLOAD).get(0);
            try (CloseableByteBuffer buffer = resource.readCloseable()) {
                assertEquals(ScanSupport.CONTENT, StandardCharsets.UTF_8.decode(buffer.getByteBuffer()).toString());
            }
        }
        Files.delete(archive);
        assertFalse(Files.exists(archive));
    }
}
