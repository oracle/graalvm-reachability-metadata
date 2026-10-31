/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JSONSerializerTest {
    @TempDir
    Path directory;

    @Test
    void preservesSharedClassIdentityAcrossRelationships() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            String json = scan.toJSON(2);
            assertTrue(json.contains("\"__ID\""));
            try (ScanResult restored = ScanResult.fromJSON(json)) {
                ClassInfo child = restored.getClassInfo(ScanFixtures.Child.class.getName());
                assertSame(ScanSupport.subject(restored), child.getSuperclass());
                assertSame(child, ScanSupport.subject(restored).getSubclasses().get(0));
            }
        }
    }
}
