/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_incremental;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.incremental.IncrementalBuildHelper;
import org.apache.maven.shared.incremental.IncrementalBuildHelperRequest;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IncrementalBuildHelperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsInputAdditionsAndRemovalsAcrossBuildExecutions() throws Exception {
        Path firstSource = Files.writeString(temporaryDirectory.resolve("First.java"), "class First {}");
        Path secondSource = Files.writeString(temporaryDirectory.resolve("Second.java"), "class Second {}");
        IncrementalBuildHelperRequest request = new IncrementalBuildHelperRequest()
                .inputFiles(Set.of(firstSource.toFile()));
        IncrementalBuildHelper helper = newHelper("compile-main");

        assertTrue(helper.inputFileTreeChanged(request));
        assertFalse(newHelper("compile-main").inputFileTreeChanged(request));

        request.setInputFiles(Set.of(secondSource.toFile(), firstSource.toFile()));
        assertTrue(helper.inputFileTreeChanged(request));
        assertFalse(helper.inputFileTreeChanged(request));

        request.setInputFiles(Set.of(secondSource.toFile()));
        assertTrue(newHelper("compile-main").inputFileTreeChanged(request));
        assertFalse(helper.inputFileTreeChanged(request));

        request.setInputFiles(Set.of());
        assertTrue(helper.inputFileTreeChanged(request));
        assertFalse(helper.inputFileTreeChanged(request));
    }

    @Test
    void scansOnlyIncludedSourcesAndKeepsExecutionStatusSeparate() throws Exception {
        Path sources = Files.createDirectory(temporaryDirectory.resolve("sources"));
        Path firstSource = Files.writeString(sources.resolve("First.java"), "class First {}");
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sources.toFile());
        scanner.setIncludes(new String[]{"**/*.java"});
        IncrementalBuildHelper helper = newHelper("compile-main");
        helper.setDirectoryScanner(scanner);

        assertSame(scanner, helper.getDirectoryScanner());
        assertTrue(helper.inputFileTreeChanged(scanner));
        assertFalse(newHelper("compile-main").inputFileTreeChanged(scanner));
        Files.writeString(sources.resolve("notes.txt"), "not a source file");
        assertFalse(helper.inputFileTreeChanged(scanner));

        Path secondSource = Files.writeString(sources.resolve("Second.java"), "class Second {}");
        assertTrue(helper.inputFileTreeChanged(scanner));
        assertFalse(helper.inputFileTreeChanged(scanner));
        Files.delete(firstSource);
        assertTrue(helper.inputFileTreeChanged(scanner));
        assertArrayEquals(new String[]{secondSource.getFileName().toString()}, scanner.getIncludedFiles());

        IncrementalBuildHelper otherExecution = newHelper("compile-tests");
        assertTrue(otherExecution.inputFileTreeChanged(scanner));
        assertFalse(helper.inputFileTreeChanged(scanner));
        assertFalse(otherExecution.inputFileTreeChanged(scanner));
        assertFalse(helper.getMojoStatusDirectory().equals(otherExecution.getMojoStatusDirectory()));
    }

    @Test
    void rebuildDeletesPreviousOutputsAndTracksOnlyNewFiles() throws Exception {
        Path output = Files.createDirectory(temporaryDirectory.resolve("classes"));
        Path obsolete = Files.writeString(output.resolve("Obsolete.class"), "previous output");
        Path retained = Files.writeString(output.resolve("retained.txt"), "owned by another task");
        Path source = Files.writeString(temporaryDirectory.resolve("Current.java"), "class Current {}");
        IncrementalBuildHelperRequest request = new IncrementalBuildHelperRequest()
                .inputFiles(Set.of(source.toFile())).outputDirectory(output.toFile());
        IncrementalBuildHelper helper = newHelper("compile-main");
        Path status = helper.getMojoStatusDirectory().toPath();
        assertEquals(temporaryDirectory.resolve("target").resolve("maven-status")
                .resolve("example-compiler-plugin").resolve("compile").resolve("compile-main"), status);
        assertTrue(Files.isDirectory(status));

        // Seed the persisted output list from a previous build execution.
        Files.writeString(status.resolve(IncrementalBuildHelper.CREATED_FILES_LST_FILENAME), "Obsolete.class\n");
        assertArrayEquals(new String[]{"Obsolete.class"}, helper.beforeRebuildExecution(request));
        assertFalse(Files.exists(obsolete));
        assertEquals("owned by another task", Files.readString(retained));

        Path current = Files.writeString(output.resolve("Current.class"), "current output");
        helper.afterRebuildExecution(request);
        assertEquals(Set.of("Current.class"), Set.copyOf(Files.readAllLines(
                status.resolve(IncrementalBuildHelper.CREATED_FILES_LST_FILENAME))));
        assertFalse(newHelper("compile-main").inputFileTreeChanged(request));

        IncrementalBuildHelper nextBuild = newHelper("compile-main");
        assertArrayEquals(new String[]{"Current.class"}, nextBuild.beforeRebuildExecution(request));
        assertFalse(Files.exists(current));
        assertEquals("owned by another task", Files.readString(retained));
        nextBuild.afterRebuildExecution(request);
        assertTrue(Files.readAllLines(status.resolve(IncrementalBuildHelper.CREATED_FILES_LST_FILENAME)).isEmpty());
    }

    private IncrementalBuildHelper newHelper(String executionId) {
        Build build = new Build();
        build.setDirectory(temporaryDirectory.resolve("target").toString());
        MavenProject project = new MavenProject();
        project.getModel().setBuild(build);

        PluginDescriptor plugin = new PluginDescriptor();
        plugin.setArtifactId("example-compiler-plugin");
        MojoDescriptor mojo = new MojoDescriptor();
        mojo.setPluginDescriptor(plugin);
        mojo.setGoal("compile");
        return new IncrementalBuildHelper(new MojoExecution(mojo, executionId), project);
    }
}
