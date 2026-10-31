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

import static org.junit.jupiter.api.Assertions.assertSame;

public class ScanResultTest {
    @TempDir
    Path directory;

    @Test
    void loadsKnownClassesWithAndWithoutExpectedSupertype() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            assertSame(ScanFixtures.Subject.class, scan.loadClass(ScanFixtures.Subject.class.getName(), false));
            assertSame(ScanFixtures.Subject.class, scan.loadClass(ScanFixtures.Subject.class.getName(), Runnable.class, false));
        }
    }
}
