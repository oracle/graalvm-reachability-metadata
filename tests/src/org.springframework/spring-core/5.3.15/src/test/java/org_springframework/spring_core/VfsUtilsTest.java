/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.VfsResource;

public class VfsUtilsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void vfsResourceDelegatesToJbossVfsHandle() throws Exception {
        Path file = Files.writeString(this.tempDirectory.resolve("sample.txt"), "spring-vfs", StandardCharsets.UTF_8);
        VfsResource resource = new VfsResource(new VirtualFile(file.toFile()));

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(Files.size(file));
        assertThat(resource.lastModified()).isEqualTo(file.toFile().lastModified());
        assertThat(resource.getFilename()).isEqualTo("sample.txt");
        assertThat(resource.getDescription()).contains("sample.txt");

        try (InputStream stream = resource.getInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("spring-vfs");
        }

        URL fileUrl = resource.getURL();
        URI fileUri = resource.getURI();
        File physicalFile = resource.getFile();
        assertThat(fileUrl).isEqualTo(file.toUri().toURL());
        assertThat(fileUri).isEqualTo(file.toUri());
        assertThat(physicalFile).isEqualTo(file.toFile());
    }

    @Test
    void relativeVfsResourcesAreResolvedThroughVfsUtilities() throws Exception {
        Path nestedFile = Files.createDirectories(this.tempDirectory.resolve("child")).resolve("nested.txt");
        Files.writeString(nestedFile, "nested", StandardCharsets.UTF_8);
        Path siblingFile = Files.writeString(this.tempDirectory.resolve("sibling.txt"), "sibling", StandardCharsets.UTF_8);
        VfsResource directoryResource = new VfsResource(new VirtualFile(this.tempDirectory.toFile()));

        Resource nestedResource = directoryResource.createRelative("child/nested.txt");
        Resource siblingResource = directoryResource.createRelative("sibling.txt");

        assertThat(nestedResource).isInstanceOf(VfsResource.class);
        assertThat(nestedResource.contentLength()).isEqualTo(Files.size(nestedFile));
        assertThat(nestedResource.getFilename()).isEqualTo("nested.txt");
        assertThat(siblingResource).isInstanceOf(VfsResource.class);
        assertThat(siblingResource.contentLength()).isEqualTo(Files.size(siblingFile));
        assertThat(siblingResource.getFilename()).isEqualTo("sibling.txt");
    }
}
