/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.VfsResource;
import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class VfsResourceTest {

    @Test
    void wrapsVirtualFileThroughVfsReflectionMethods() throws Exception {
        byte[] content = "vfs-resource-content".getBytes(StandardCharsets.UTF_8);
        URL url = new URL("file:/tmp/hutool-vfs-resource.txt");
        VirtualFile virtualFile = new VirtualFile("hutool-vfs-resource.txt", content, url, 123L);

        VfsResource resource = new VfsResource(virtualFile);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getName()).isEqualTo("hutool-vfs-resource.txt");
        assertThat(resource.getUrl()).isEqualTo(url);
        assertThat(resource.getLastModified()).isEqualTo(123L);
        assertThat(resource.size()).isEqualTo(content.length);
        assertThat(resource.isModified()).isFalse();

        try (InputStream stream = resource.getStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("vfs-resource-content");
        }

        virtualFile.setLastModified(456L);
        assertThat(resource.isModified()).isTrue();
    }
}
