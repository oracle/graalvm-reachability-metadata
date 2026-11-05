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

public class ClassFieldsTest {
    @TempDir
    Path directory;

    @Test
    void preservesNonPublicMemberDescriptionsInJson() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2);
                ScanResult restored = ScanResult.fromJSON(scan.toJSON())) {
            assertTrue(ScanSupport.subject(restored).getFieldInfo("hidden").isPrivate());
            assertTrue(ScanSupport.subject(restored).getMethodInfo("secret").get(0).isPrivate());
            assertEquals("java.lang.String[][]", ScanSupport.subject(restored)
                    .getFieldInfo("matrix").getTypeDescriptor().toString());
            assertEquals("catalog", ScanSupport.subject(restored).getFieldInfo("TITLE").getConstantInitializerValue());
        }
    }
}
