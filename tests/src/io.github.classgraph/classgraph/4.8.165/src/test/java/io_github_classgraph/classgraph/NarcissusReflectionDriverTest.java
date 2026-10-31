/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraph.CircumventEncapsulationMethod;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NarcissusReflectionDriverTest {
    @TempDir
    Path directory;

    @Test
    void discoversClasspathAndReadsEnumsAndAnnotationsUsingNarcissus() throws Exception {
        CircumventEncapsulationMethod previous = ClassGraph.CIRCUMVENT_ENCAPSULATION;
        PrintStream previousError = System.err;
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        try (PrintStream error = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            System.setErr(error);
            ClassGraph.CIRCUMVENT_ENCAPSULATION = CircumventEncapsulationMethod.NARCISSUS;
            try (ScanResult scan = ScanSupport.discover(ScanSupport.archive(directory))) {
                assertTrue(scan.getClassesImplementing(Runnable.class.getName()).getNames()
                        .contains(ScanFixtures.Subject.class.getName()));
                assertEquals(ScanSupport.CONTENT,
                        scan.getResourcesWithPath(ScanSupport.PAYLOAD).get(0).getContentAsString());
                assertEquals(List.of(ScanFixtures.Mode.FAST, ScanFixtures.Mode.SAFE),
                        scan.getClassInfo(ScanFixtures.Mode.class.getName()).getEnumConstantObjects());
                ScanFixtures.Tag subjectTag = (ScanFixtures.Tag) ScanSupport.subject(scan)
                        .getAnnotationInfo(ScanFixtures.Tag.class.getName()).loadClassAndInstantiate();
                ScanFixtures.Tag childTag = (ScanFixtures.Tag) scan.getClassInfo(ScanFixtures.Child.class.getName())
                        .getAnnotationInfo(ScanFixtures.Tag.class.getName()).loadClassAndInstantiate();
                assertEquals("catalog", subjectTag.value());
                assertEquals(subjectTag, childTag);
                assertEquals(subjectTag.hashCode(), childTag.hashCode());
            }
        } finally {
            ClassGraph.CIRCUMVENT_ENCAPSULATION = previous;
            System.setErr(previousError);
        }
        String output = diagnostics.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("Could not load Narcissus reflection driver"), output);
    }
}
