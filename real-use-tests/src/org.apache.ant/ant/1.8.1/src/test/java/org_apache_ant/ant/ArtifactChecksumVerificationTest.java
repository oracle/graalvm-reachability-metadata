/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Checksum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Ant's checksum subsystem to publish and verify an application artifact.
public class ArtifactChecksumVerificationTest {
    @Test
    @Timeout(60)
    public void verifiesPublishedArtifactAndRejectsChangedContent() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-artifact-checksum-");
        try {
            Path artifact = workDirectory.resolve("dist/inventory.jar");
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, "inventory release 2026.04\n", StandardCharsets.UTF_8);
            Path checksumDirectory = Files.createDirectories(workDirectory.resolve("checksums"));

            Project publishingProject = new Project();
            publishingProject.init();
            Checksum publisher = checksumTask(publishingProject, artifact, checksumDirectory);
            publisher.execute();

            Path checksumFile = checksumDirectory.resolve("inventory.jar.sha256");
            assertTrue(Files.isRegularFile(checksumFile));
            assertEquals(64, Files.readString(checksumFile, StandardCharsets.UTF_8)
                    .trim().length());

            Project verificationProject = new Project();
            verificationProject.init();
            Checksum verifier = checksumTask(verificationProject, artifact, checksumDirectory);
            verifier.setVerifyproperty("artifact.integrity");
            verifier.execute();
            assertEquals("true", verificationProject.getProperty("artifact.integrity"));

            Files.writeString(artifact, "inventory release 2026.05\n", StandardCharsets.UTF_8);
            Project changedProject = new Project();
            changedProject.init();
            Checksum changedVerifier = checksumTask(changedProject, artifact, checksumDirectory);
            changedVerifier.setVerifyproperty("artifact.integrity");
            changedVerifier.execute();
            assertEquals("false", changedProject.getProperty("artifact.integrity"));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static Checksum checksumTask(Project project, Path artifact, Path checksumDirectory) {
        Checksum checksum = new Checksum();
        checksum.setProject(project);
        checksum.setFile(artifact.toFile());
        checksum.setTodir(checksumDirectory.toFile());
        checksum.setAlgorithm("SHA-256");
        checksum.setFileext(".sha256");
        return checksum;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ArtifactChecksumVerificationTest::delete);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean up " + path, exception);
        }
    }
}
