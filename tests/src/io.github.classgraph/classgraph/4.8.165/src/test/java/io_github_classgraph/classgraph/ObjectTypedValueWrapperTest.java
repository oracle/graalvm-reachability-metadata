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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectTypedValueWrapperTest {
    @TempDir
    Path directory;

    @Test
    void instantiatesTypedAnnotationArrays() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ScanFixtures.Details details = (ScanFixtures.Details) ScanSupport.details(scan).loadClassAndInstantiate();
            assertArrayEquals(new ScanFixtures.Mode[]{ScanFixtures.Mode.FAST, ScanFixtures.Mode.SAFE}, details.alternatives());
            assertArrayEquals(new Class<?>[]{String.class, Integer.class}, details.types());
            assertArrayEquals(new int[]{3, 5, 8}, details.numbers());
            assertEquals(Arrays.asList("first", "second"), Arrays.stream(details.tags())
                    .map(ScanFixtures.Tag::value).toList());
        }
    }
}
