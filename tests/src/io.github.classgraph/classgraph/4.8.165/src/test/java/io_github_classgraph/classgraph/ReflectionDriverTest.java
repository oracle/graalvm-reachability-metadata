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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ReflectionDriverTest {
    @TempDir
    Path directory;

    @Test
    void distinguishesAnnotationsWithDifferentParameterValues() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ScanFixtures.Tag tag = (ScanFixtures.Tag) ScanSupport.subject(scan)
                    .getAnnotationInfo(ScanFixtures.Tag.class.getName()).loadClassAndInstantiate();
            ScanFixtures.Details details = (ScanFixtures.Details) ScanSupport.details(scan).loadClassAndInstantiate();
            assertNotEquals(tag, details.nested());
            assertNotEquals(details.nested(), tag);
        }
    }
}
