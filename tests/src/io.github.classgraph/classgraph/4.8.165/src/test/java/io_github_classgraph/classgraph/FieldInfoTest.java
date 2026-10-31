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

import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldInfoTest {
    @TempDir
    Path directory;

    @Test
    void resolvesPublicAndPrivateFields() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ClassInfo info = ScanSupport.subject(scan);
            assertEquals("reader", info.getFieldInfo("name").loadClassAndGetField()
                    .get(new ScanFixtures.Subject()));
            assertTrue(Modifier.isPrivate(info.getFieldInfo("hidden").loadClassAndGetField().getModifiers()));
            assertEquals("catalog", info.getFieldInfo("TITLE").getConstantInitializerValue());
        }
    }
}
