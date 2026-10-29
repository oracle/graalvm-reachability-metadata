/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_file;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.providers.file.FileWagon;
import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class FileWagonTest {
    @TempDir
    Path tempDir;

    private Path repositoryDir;
    private FileWagon wagon;

    @BeforeEach
    void connect() throws Exception {
        repositoryDir = tempDir.resolve("repository");
        wagon = new FileWagon();
        wagon.connect(new Repository("local", repositoryDir.toUri().toString()));
    }

    @AfterEach
    void disconnect() throws Exception {
        wagon.disconnect();
    }

    @Test
    void createsRepositoryOnConnect() throws Exception {
        assertTrue(Files.isDirectory(repositoryDir));
        assertTrue(wagon.resourceExists("."));
        assertThat(wagon.getFileList(".")).isEmpty();
    }

    @Test
    void uploadsAndDownloadsBinaryFileInNestedDirectories() throws Exception {
        byte[] content = new byte[] {0, 1, 2, 127, -128, -1, 10};
        Path source = Files.write(tempDir.resolve("artifact.bin"), content);

        wagon.put(source.toFile(), "artifacts/releases/artifact.bin");

        assertArrayEquals(content, Files.readAllBytes(repositoryDir.resolve("artifacts/releases/artifact.bin")));
        assertTrue(wagon.resourceExists("artifacts/releases/artifact.bin"));
        Path downloaded = tempDir.resolve("downloads/nested/artifact.bin");
        wagon.get("artifacts/releases/artifact.bin", downloaded.toFile());
        assertArrayEquals(content, Files.readAllBytes(downloaded));
    }

    @Test
    void overwritesAnExistingArtifact() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.txt"), "original artifact contents");
        wagon.put(source.toFile(), "artifact.txt");
        Files.writeString(source, "replacement");

        wagon.put(source.toFile(), "artifact.txt");

        assertEquals("replacement", Files.readString(repositoryDir.resolve("artifact.txt")));
        Path downloaded = Files.writeString(tempDir.resolve("download.txt"), "previous download with a longer payload");
        wagon.get("artifact.txt", downloaded.toFile());
        assertEquals("replacement", Files.readString(downloaded));
    }

    @Test
    void downloadsOnlyResourcesNewerThanTheSuppliedTimestamp() throws Exception {
        Path artifact = Files.writeString(repositoryDir.resolve("artifact.txt"), "remote contents");
        Files.setLastModifiedTime(artifact, FileTime.fromMillis(1600000000000L));
        long modified = Files.getLastModifiedTime(artifact).toMillis();
        Path downloaded = Files.writeString(tempDir.resolve("cached.txt"), "cached contents");

        assertFalse(wagon.getIfNewer("artifact.txt", downloaded.toFile(), modified + 60000L));
        assertEquals("cached contents", Files.readString(downloaded));
        assertTrue(wagon.getIfNewer("artifact.txt", downloaded.toFile(), modified - 60000L));
        assertEquals("remote contents", Files.readString(downloaded));
    }

    @Test
    void copiesDirectoryTreesAndListsTheirImmediateChildren() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("site"));
        Files.writeString(source.resolve("index.html"), "<html>Local repository</html>");
        Path assets = Files.createDirectories(source.resolve("assets"));
        Files.writeString(assets.resolve("style.css"), "body { color: blue; }");

        assertTrue(wagon.supportsDirectoryCopy());
        wagon.putDirectory(source.toFile(), "published/site");

        assertEquals("<html>Local repository</html>", Files.readString(repositoryDir.resolve("published/site/index.html")));
        assertEquals("body { color: blue; }", Files.readString(repositoryDir.resolve("published/site/assets/style.css")));
        assertThat(wagon.getFileList("published/site")).containsExactlyInAnyOrder("index.html", "assets");
        assertThat(wagon.getFileList("published/site/assets")).containsExactly("style.css");
    }

    @Test
    void copiesADirectoryToTheRepositoryRoot() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("root-content"));
        Files.writeString(source.resolve("readme.txt"), "repository documentation");

        wagon.putDirectory(source.toFile(), ".");

        assertEquals("repository documentation", Files.readString(repositoryDir.resolve("readme.txt")));
        assertThat(wagon.getFileList(".")).containsExactly("readme.txt");
    }

    @Test
    void normalizesBackslashesInDirectoryAndResourcePaths() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(source.resolve("entry.txt"), "portable path");

        wagon.putDirectory(source.toFile(), "published\\content");

        assertEquals("portable path", Files.readString(repositoryDir.resolve("published/content/entry.txt")));
        assertTrue(wagon.resourceExists("published\\content\\entry.txt"));
        assertThat(wagon.getFileList("published\\content")).containsExactly("entry.txt");
    }

    @Test
    void reportsMissingResources() throws Exception {
        assertFalse(wagon.resourceExists("missing.txt"));
        Path destination = tempDir.resolve("missing-download.txt");

        assertThrows(ResourceDoesNotExistException.class, () -> wagon.get("missing.txt", destination.toFile()));
        assertFalse(Files.exists(destination));
        assertThrows(ResourceDoesNotExistException.class, () -> wagon.getFileList("missing-directory"));
    }

    @Test
    void rejectsListingAFileAsADirectory() throws Exception {
        Files.writeString(repositoryDir.resolve("artifact.txt"), "artifact contents");

        assertTrue(wagon.resourceExists("artifact.txt"));
        assertThrows(ResourceDoesNotExistException.class, () -> wagon.getFileList("artifact.txt"));
    }
}
