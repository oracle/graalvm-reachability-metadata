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

public class TypeResolutionsTest {
    @TempDir
    Path directory;

    @Test
    void restoresTypedMemberInfoLists() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2);
                ScanResult restored = ScanResult.fromJSON(scan.toJSON())) {
            assertEquals("greet", ScanSupport.subject(restored).getMethodInfo("greet").get(0).getName());
            assertEquals("java.lang.String", ScanSupport.subject(restored)
                    .getMethodInfo("greet").get(0).getParameterInfo()[0].getTypeDescriptor().toString());
            assertEquals(ScanSupport.subject(scan).getFieldInfo().getNames(),
                    ScanSupport.subject(restored).getFieldInfo().getNames());
        }
    }
}
