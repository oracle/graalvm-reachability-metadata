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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationInfoTest {
    @TempDir
    Path directory;

    @Test
    void instantiatesAnnotationWithScalarDefaults() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ScanFixtures.Details details = (ScanFixtures.Details) ScanSupport.details(scan).loadClassAndInstantiate();
            assertSame(ScanFixtures.Details.class, details.annotationType());
            assertSame(ScanFixtures.Mode.SAFE, details.mode());
            assertSame(String.class, details.type());
            assertEquals("inner", details.nested().value());
            assertTrue(details.enabled());
            assertEquals((byte) 7, details.small());
            assertEquals((short) 12, details.count());
            assertEquals('C', details.initial());
            assertEquals(42, details.size());
            assertEquals(5000000000L, details.total());
            assertEquals(1.25f, details.ratio());
            assertEquals(2.5, details.weight());
        }
    }
}
