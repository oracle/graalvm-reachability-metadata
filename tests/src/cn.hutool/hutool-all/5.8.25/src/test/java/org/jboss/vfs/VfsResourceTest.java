/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.vfs;

import cn.hutool.core.io.resource.VfsResource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class VfsResourceTest {
    @Test
    public void readsVirtualFileAttributesThroughVfsResource() throws Exception {
        String text = "content served through a virtual file";
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        URL url = new URL("file:/virtual/vfs-resource.txt");
        VirtualFile virtualFile = new VirtualFile("vfs-resource.txt", content, 1_234L, url);

        VfsResource resource = new VfsResource(virtualFile);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getName()).isEqualTo("vfs-resource.txt");
        assertThat(resource.getUrl()).isEqualTo(url);
        assertThat(resource.size()).isEqualTo(content.length);
        assertThat(resource.getLastModified()).isEqualTo(1_234L);
        assertThat(resource.readUtf8Str()).isEqualTo(text);
        assertThat(resource.isModified()).isFalse();

        virtualFile.touch(5_678L);

        assertThat(resource.isModified()).isTrue();
    }
}

final class VirtualFile {
    private final String name;
    private final byte[] content;
    private final URL url;
    private long lastModified;

    VirtualFile(String name, byte[] content, long lastModified, URL url) {
        this.name = name;
        this.content = content;
        this.lastModified = lastModified;
        this.url = url;
    }

    public boolean exists() {
        return true;
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(content);
    }

    public long getSize() {
        return content.length;
    }

    public long getLastModified() {
        return lastModified;
    }

    public URL toURL() {
        return url;
    }

    public String getName() {
        return name;
    }

    void touch(long lastModified) {
        this.lastModified = lastModified;
    }
}
