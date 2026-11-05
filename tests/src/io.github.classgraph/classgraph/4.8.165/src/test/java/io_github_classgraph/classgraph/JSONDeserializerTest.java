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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JSONDeserializerTest {
    @TempDir
    Path directory;

    @Test
    void restoresNestedAnnotationObjectsAndArrays() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2);
                ScanResult restored = ScanResult.fromJSON(scan.toJSON())) {
            ScanFixtures.Details details = (ScanFixtures.Details) ScanSupport.details(restored).loadClassAndInstantiate();
            assertArrayEquals(new int[]{3, 5, 8}, details.numbers());
            assertEquals("first", details.tags()[0].value());
            assertEquals("second", details.tags()[1].value());
            assertEquals(ScanFixtures.Mode.SAFE, details.mode());
            assertArrayEquals(new Class<?>[]{String.class, Integer.class}, details.types());
        }
    }
}
