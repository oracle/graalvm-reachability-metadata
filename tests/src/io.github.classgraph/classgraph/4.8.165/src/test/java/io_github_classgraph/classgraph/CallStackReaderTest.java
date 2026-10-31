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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallStackReaderTest {
    @TempDir
    Path directory;

    @Test
    void discoversTheCallingApplicationsClasspath() throws Exception {
        Path archive = ScanSupport.archive(directory);
        try (ScanResult scan = ScanSupport.discover(archive)) {
            assertEquals(List.of(archive.toFile()), scan.getClasspathFiles());
            assertEquals(ScanFixtures.Subject.class.getName(), ScanSupport.subject(scan).getName());
        }
    }
}
