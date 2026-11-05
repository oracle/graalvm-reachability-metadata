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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ScanSpecTest {
    @TempDir
    Path directory;

    @Test
    void appliesAndLogsClassAcceptanceRules() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).verbose()
                .rejectClasses(ScanFixtures.Child.class.getName()).scan(2)) {
            assertNotNull(ScanSupport.subject(scan));
            assertFalse(scan.getAllClasses().getNames().contains(ScanFixtures.Child.class.getName()));
        }
    }
}
