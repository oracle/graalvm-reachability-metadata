/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ArrayTypeSignature;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArrayTypeSignatureTest {
    @TempDir
    Path directory;

    @Test
    void loadsPrimitiveAndMultidimensionalArrayTypes() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ArrayTypeSignature matrix = (ArrayTypeSignature) ScanSupport.subject(scan)
                    .getFieldInfo("matrix").getTypeDescriptor();
            assertEquals(2, matrix.getNumDimensions());
            assertSame(String.class, matrix.loadElementClass());
            assertSame(String[][].class, matrix.loadClass());
            ArrayTypeSignature numbers = (ArrayTypeSignature) ScanSupport.subject(scan)
                    .getFieldInfo("numbers").getTypeDescriptor();
            assertSame(int.class, numbers.loadElementClass());
            assertSame(int[].class, numbers.loadClass());
        }
    }
}
