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

public class ClassFieldCacheTest {
    @TempDir
    Path directory;

    @Test
    void restoresClassCollectionsAndRelationshipMaps() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2);
                ScanResult restored = ScanResult.fromJSON(scan.toJSON())) {
            assertEquals(scan.getSubclasses(ScanFixtures.Subject.class.getName()).getNames(),
                    restored.getSubclasses(ScanFixtures.Subject.class.getName()).getNames());
            assertEquals(scan.getClassesImplementing(Runnable.class.getName()).getNames(),
                    restored.getClassesImplementing(Runnable.class.getName()).getNames());
            assertEquals(scan.getClassesWithAnnotation(ScanFixtures.Tag.class.getName()).getNames(),
                    restored.getClassesWithAnnotation(ScanFixtures.Tag.class.getName()).getNames());
        }
    }
}
