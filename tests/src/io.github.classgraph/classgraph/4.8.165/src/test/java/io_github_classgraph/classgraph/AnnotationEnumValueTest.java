/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.AnnotationEnumValue;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AnnotationEnumValueTest {
    @TempDir
    Path directory;

    @Test
    void resolvesAnnotationEnumDefaults() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            AnnotationEnumValue mode = (AnnotationEnumValue) ScanSupport.details(scan)
                    .getParameterValues().getValue("mode");
            assertEquals("SAFE", mode.getValueName());
            assertSame(ScanFixtures.Mode.SAFE, mode.loadClassAndReturnEnumValue());
        }
    }
}
