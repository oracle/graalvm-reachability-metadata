/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_aether_provider;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.repository.internal.DefaultServiceLocator;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RepositoryFixture {
    private final Path directory;
    private final RepositorySystem system;
    private final MavenRepositorySystemSession session;

    RepositoryFixture(Path directory) {
        this.directory = directory;
        system = new DefaultServiceLocator().getService(RepositorySystem.class);
        assertNotNull(system);
        session = new MavenRepositorySystemSession();
        session.setOffline(true);
        session.setIgnoreMissingArtifactDescriptor(false);
        session.setIgnoreInvalidArtifactDescriptor(false);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(
                new LocalRepository(directory.resolve("repository").toFile())));
    }

    RepositorySystem system() {
        return system;
    }

    MavenRepositorySystemSession session() {
        return session;
    }

    Artifact artifact(String name, String extension, String version) {
        return new DefaultArtifact("test.provider", name, "", extension, version);
    }

    Artifact install(String name, String extension, String version, String content) throws Exception {
        Path source = Files.createTempFile(directory, "artifact-", "." + extension);
        Files.writeString(source, content);
        Artifact artifact = artifact(name, extension, version).setFile(source.toFile());
        system.install(session, new InstallRequest().addArtifact(artifact));
        return artifact;
    }

    void installPom(String name, String version, String elements) throws Exception {
        install(name, "pom", version, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test.provider</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  %s
                </project>
                """.formatted(name, version, elements));
    }
}
