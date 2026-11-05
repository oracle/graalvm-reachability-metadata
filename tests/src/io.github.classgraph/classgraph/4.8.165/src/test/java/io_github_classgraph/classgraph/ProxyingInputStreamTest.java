/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyingInputStreamTest {
    @Test
    void readsModuleClassResourcesWithModernStreamMethods() throws Exception {
        try (ScanResult scan = new ClassGraph().enableSystemJarsAndModules().acceptModules("java.base")
                .acceptPathsNonRecursive("java/lang").scan(2)) {
            Resource object = scan.getResourcesWithPath("java/lang/Object.class").get(0);
            byte[] complete;
            try (InputStream input = object.open()) {
                complete = input.readAllBytes();
            }
            assertTrue(complete.length > 4);
            try (InputStream input = object.open()) {
                assertArrayEquals(new byte[]{(byte) 0xca, (byte) 0xfe}, input.readNBytes(2));
                byte[] suffix = new byte[4];
                assertEquals(2, input.readNBytes(suffix, 1, 2));
                assertEquals((byte) 0xba, suffix[1]);
                assertEquals((byte) 0xbe, suffix[2]);
                input.skipNBytes(4);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                assertEquals(complete.length - 8L, input.transferTo(output));
                assertEquals(complete.length - 8, output.size());
            }
        }
    }
}
