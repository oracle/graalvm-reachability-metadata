/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.packagescan.resource.VfsResource;
import org.jboss.vfs.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VfsUtilsTest {
    @TempDir
    Path directory;

    @Test
    void readsVirtualFileAttributesAndContentThroughNacos() throws Exception {
        Path file = Files.writeString(directory.resolve("configuration.txt"), "cluster-data");
        VfsResource resource = new VfsResource(VFS.getChild(file.toUri()));
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        assertEquals("configuration.txt", resource.getFilename());
        assertEquals(Files.size(file), resource.contentLength());
        assertEquals(file.toFile().lastModified(), resource.lastModified());
        assertEquals(file.toRealPath(), resource.getFile().toPath().toRealPath());
        assertEquals("vfs", resource.getUrl().getProtocol());
        assertEquals("vfs", resource.getUri().getScheme());
        try (InputStream stream = resource.getInputStream()) {
            assertEquals("cluster-data", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void resolvesRelativeChildrenOfVirtualDirectories() throws Exception {
        Files.createDirectories(directory.resolve("nested"));
        Files.writeString(directory.resolve("direct.txt"), "direct");
        Files.writeString(directory.resolve("nested").resolve("child.txt"), "nested");
        VfsResource root = new VfsResource(VFS.getChild(directory.toUri()));
        Resource direct = root.createRelative("direct.txt");
        Resource nested = root.createRelative("nested/child.txt");
        try (InputStream directStream = direct.getInputStream(); InputStream nestedStream = nested.getInputStream()) {
            assertEquals("direct", new String(directStream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("nested", new String(nestedStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
