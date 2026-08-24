/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.VfsResource;

/** Verifies Spring's public JBoss VFS resource integration. §FS-repository-functional-spec.5.2 */
public class VfsUtilsTest {
    private static final byte[] CONTENT = "Spring VFS resource content".getBytes(UTF_8);

    @TempDir
    private Path temporaryDirectory;

    @Test
    void readsVirtualFileUsingSpringResourceOperations() throws Exception {
        Path source = temporaryDirectory.resolve("spring-vfs.txt");
        Files.write(source, CONTENT);
        VirtualFile virtualFile = VFS.getChild(source.toUri());
        VfsResource resource = new VfsResource(virtualFile);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(CONTENT.length);
        assertThat(resource.lastModified()).isEqualTo(source.toFile().lastModified());
        assertThat(resource.getFilename()).isEqualTo(source.getFileName().toString());
        assertThat(resource.getURL().getProtocol()).isEqualTo("vfs");
        assertThat(resource.getURI().getScheme()).isEqualTo("vfs");
        assertThat(resource.getFile().toPath().toRealPath()).isEqualTo(source.toRealPath());
        try (InputStream input = resource.getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(CONTENT);
        }
    }

    @Test
    void createsRelativeVirtualFileResources() throws Exception {
        Path directFile = temporaryDirectory.resolve("direct.txt");
        Path nestedFile = temporaryDirectory.resolve("nested").resolve("nested.txt");
        Files.createDirectories(nestedFile.getParent());
        Files.write(directFile, CONTENT);
        Files.write(nestedFile, CONTENT);
        VfsResource directory = new VfsResource(VFS.getChild(temporaryDirectory.toUri()));

        Resource directResource = directory.createRelative("direct.txt");
        Resource nestedResource = directory.createRelative("nested/nested.txt");

        try (InputStream directInput = directResource.getInputStream();
                InputStream nestedInput = nestedResource.getInputStream()) {
            assertThat(directInput.readAllBytes()).isEqualTo(CONTENT);
            assertThat(nestedInput.readAllBytes()).isEqualTo(CONTENT);
        }
    }
}
