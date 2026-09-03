/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.command.core.init.InitProjectUtil;
import liquibase.command.core.init.InitProjectUtil.FileCreationResultEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InitProjectUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void copyExampleFilesLoadsBundledResources() throws Exception {
        Path defaultsFile = InitProjectUtil.copyExampleProperties(
                InitProjectUtil.XML,
                tempDir.toString(),
                "liquibase-test.properties").toPath();
        assertThat(defaultsFile).exists();
        assertThat(Files.readString(defaultsFile)).contains("changeLogFile");

        FileCreationResultEnum changelogResult = InitProjectUtil.copyExampleChangelog(
                InitProjectUtil.SQL,
                tempDir.toFile(),
                null,
                null);
        assertThat(changelogResult).isEqualTo(FileCreationResultEnum.created);
        Path changelogFile = tempDir.resolve("example-changelog.sql");
        assertThat(changelogFile).exists();
        assertThat(Files.readString(changelogFile)).contains("--liquibase formatted sql");
    }

    @Test
    void copyExampleFlowAndChecksFilesLoadsClasspathResources() throws Exception {
        List<Path> createdPaths = new ArrayList<>();
        try {
            createRelativeResourceMarkers(createdPaths);

            InitProjectUtil.copyExampleFlowFiles(InitProjectUtil.SQL, tempDir.toFile());
            InitProjectUtil.copyChecksPackageFile(InitProjectUtil.SQL, tempDir.toFile());

            Path flowFile = tempDir.resolve("liquibase.flowfile.yaml");
            Path checksPackageFile = tempDir.resolve("liquibase.checks-package.yaml");
            assertThat(flowFile).exists();
            assertThat(Files.readString(flowFile)).contains("sql-example-flow");
            assertThat(tempDir.resolve("liquibase.advanced.flowfile.yaml")).exists();
            assertThat(tempDir.resolve("liquibase.endstage.flow")).exists();
            assertThat(tempDir.resolve("liquibase.flowvariables.yaml")).exists();
            assertThat(checksPackageFile).exists();
            assertThat(Files.readString(checksPackageFile)).contains("checks-package");
        } finally {
            Collections.reverse(createdPaths);
            for (Path path : createdPaths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void createRelativeResourceMarkers(List<Path> createdPaths) throws Exception {
        Path exampleDirectory = Path.of("liquibase", "examples", InitProjectUtil.SQL);
        createDirectoryIfMissing(exampleDirectory.getParent().getParent(), createdPaths);
        createDirectoryIfMissing(exampleDirectory.getParent(), createdPaths);
        createDirectoryIfMissing(exampleDirectory, createdPaths);

        createFileIfMissing(exampleDirectory.resolve("liquibase.flowfile.yaml"), createdPaths);
        createFileIfMissing(exampleDirectory.resolve("liquibase.advanced.flowfile.yaml"), createdPaths);
        createFileIfMissing(exampleDirectory.resolve("liquibase.endstage.flow"), createdPaths);
        createFileIfMissing(exampleDirectory.resolve("liquibase.flowvariables.yaml"), createdPaths);
        createFileIfMissing(exampleDirectory.resolve("liquibase.checks-package.yaml"), createdPaths);
    }

    private static void createDirectoryIfMissing(Path path, List<Path> createdPaths) throws Exception {
        if (Files.notExists(path)) {
            Files.createDirectory(path);
            createdPaths.add(path);
        }
    }

    private static void createFileIfMissing(Path path, List<Path> createdPaths) throws Exception {
        if (Files.notExists(path)) {
            Files.writeString(path, "name: sql-example-flow\nchecks-package:\n");
            createdPaths.add(path);
        }
    }
}
