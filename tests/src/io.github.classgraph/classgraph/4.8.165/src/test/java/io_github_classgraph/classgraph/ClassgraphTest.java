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

public class ClassgraphTest {
    @TempDir
    Path directory;

    @Test
    void scansApplicationClassesAndResources() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            assertTrue(scan.getAllClasses().getNames().contains(ScanFixtures.Subject.class.getName()));
            assertTrue(ScanSupport.subject(scan).implementsInterface(Runnable.class.getName()));
            assertEquals(ScanSupport.CONTENT, scan.getResourcesWithPath(ScanSupport.PAYLOAD).get(0).getContentAsString());
        }
    }
}
