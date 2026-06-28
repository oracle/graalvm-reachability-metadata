/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.VfsResource;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class VfsResourceTest {
    @TempDir
    Path tempDir;

    @Test
    void wrapsJbossVirtualFileResource() throws Exception {
        byte[] content = "hello from vfs".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("sample-vfs-resource.txt");
        Files.write(file, content);

        VirtualFile virtualFile = VFS.getChild(file.toUri());
        VfsResource resource = new VfsResource(virtualFile);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getName()).isEqualTo(file.getFileName().toString());
        assertThat(resource.size()).isEqualTo(content.length);
        assertThat(resource.getLastModified()).isPositive();
        assertThat(resource.isModified()).isFalse();

        URL url = resource.getUrl();
        assertThat(url.toString()).contains(file.getFileName().toString());

        try (InputStream stream = resource.getStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(content);
        }
    }
}
