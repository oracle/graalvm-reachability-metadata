/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldTypeInfoTest {
    @TempDir
    Path directory;

    @Test
    void restoresBooleanAndIntegerScanOptions() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory))
                .setMaxBufferedJarRAMSize(2048).scan(2);
                ScanResult restored = ScanResult.fromJSON(scan.toJSON())) {
            assertTrue(restored.isObtainedFromDeserialization());
            assertEquals(scan.getAllClasses().getNames(), restored.getAllClasses().getNames());
            String json = restored.toJSON(2);
            assertTrue(json.contains("\"maxBufferedJarRAMSize\": 2048"));
            assertTrue(json.contains("\"enableAnnotationInfo\": true"));
        }
    }
}
