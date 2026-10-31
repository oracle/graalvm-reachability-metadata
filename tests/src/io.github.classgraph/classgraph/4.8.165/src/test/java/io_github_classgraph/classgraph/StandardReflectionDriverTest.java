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

public class StandardReflectionDriverTest {
    @TempDir
    Path directory;

    @Test
    void readsEnumConstantObjectsInDeclarationOrder() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            assertEquals(List.of("FAST", "SAFE"), scan.getClassInfo(ScanFixtures.Mode.class.getName())
                    .getEnumConstants().getNames());
            assertEquals(List.of(ScanFixtures.Mode.FAST, ScanFixtures.Mode.SAFE),
                    scan.getClassInfo(ScanFixtures.Mode.class.getName()).getEnumConstantObjects());
        }
    }

    @Test
    void comparesAnnotationInstancesThroughTheirPublicContract() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ScanFixtures.Tag subjectTag = (ScanFixtures.Tag) ScanSupport.subject(scan)
                    .getAnnotationInfo(ScanFixtures.Tag.class.getName()).loadClassAndInstantiate();
            ScanFixtures.Tag childTag = (ScanFixtures.Tag) scan.getClassInfo(ScanFixtures.Child.class.getName())
                    .getAnnotationInfo(ScanFixtures.Tag.class.getName()).loadClassAndInstantiate();
            assertEquals("catalog", subjectTag.value());
            assertEquals(subjectTag, childTag);
            assertEquals(subjectTag.hashCode(), childTag.hashCode());
        }
    }
}
