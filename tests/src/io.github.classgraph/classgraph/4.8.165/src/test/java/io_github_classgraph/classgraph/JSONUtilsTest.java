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

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JSONUtilsTest {
    @TempDir
    Path directory;

    @Test
    void serializesPrivateScanStateAndNumericAnnotationValues() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).setMaxBufferedJarRAMSize(4096).scan(2)) {
            String json = scan.toJSON(2);
            assertTrue(json.contains("\"enableClassInfo\": true"));
            assertTrue(json.contains("\"maxBufferedJarRAMSize\": 4096"));
            assertTrue(json.contains("\"longValue\": 5000000000"));
            assertTrue(json.contains("\"floatValue\": 1.25"));
            assertTrue(json.contains("\"doubleValue\": 2.5"));
        }
    }
}
