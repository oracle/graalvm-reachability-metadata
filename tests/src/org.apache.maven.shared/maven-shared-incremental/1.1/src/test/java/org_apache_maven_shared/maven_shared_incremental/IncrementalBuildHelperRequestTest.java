/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.maven.shared.incremental.IncrementalBuildHelperRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IncrementalBuildHelperRequestTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultInputFilesCanBePopulatedByTheCaller() throws Exception {
        IncrementalBuildHelperRequest request = new IncrementalBuildHelperRequest();
        assertTrue(request.getInputFiles().isEmpty());
        assertNull(request.getOutputDirectory());

        File source = Files.createFile(temporaryDirectory.resolve("Source.java")).toFile();
        request.getInputFiles().add(source);
        assertEquals(Set.of(source), request.getInputFiles());
    }

    @Test
    void fluentAndBeanSettersConfigureAndReplaceBuildInputs() throws Exception {
        File firstSource = Files.createFile(temporaryDirectory.resolve("First.java")).toFile();
        File secondSource = Files.createFile(temporaryDirectory.resolve("Second.java")).toFile();
        File firstOutput = Files.createDirectory(temporaryDirectory.resolve("classes")).toFile();
        File secondOutput = Files.createDirectory(temporaryDirectory.resolve("test-classes")).toFile();
        IncrementalBuildHelperRequest request = new IncrementalBuildHelperRequest();

        assertSame(request, request.inputFiles(Set.of(firstSource)));
        assertSame(request, request.outputDirectory(firstOutput));
        assertEquals(Set.of(firstSource), request.getInputFiles());
        assertEquals(firstOutput, request.getOutputDirectory());

        request.setInputFiles(Set.of(secondSource));
        request.setOutputDirectory(secondOutput);
        assertEquals(Set.of(secondSource), request.getInputFiles());
        assertEquals(secondOutput, request.getOutputDirectory());
    }
}
