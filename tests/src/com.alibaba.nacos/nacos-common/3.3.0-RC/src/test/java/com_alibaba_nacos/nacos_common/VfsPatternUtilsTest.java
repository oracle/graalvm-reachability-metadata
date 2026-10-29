/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.resource.DefaultResourceLoader;
import com.alibaba.nacos.common.packagescan.resource.PathMatchingResourcePatternResolver;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.packagescan.resource.VfsResource;
import org.jboss.vfs.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VfsPatternUtilsTest {
    @TempDir
    Path directory;

    @Test
    void visitsVirtualDirectoriesToFindMatchingResources() throws Exception {
        Files.createDirectories(directory.resolve("nested"));
        Files.writeString(directory.resolve("one.properties"), "one");
        Files.writeString(directory.resolve("nested").resolve("two.properties"), "two");
        Files.writeString(directory.resolve("nested").resolve("ignored.txt"), "ignored");
        DefaultResourceLoader loader = new DefaultResourceLoader();
        loader.addProtocolResolver((location, resourceLoader) -> location.startsWith("vfs:")
                ? new VfsResource(VFS.getChild(URI.create(location))) : null);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        String rootUrl = VFS.getChild(directory.toUri()).toURL().toExternalForm();
        Resource[] matches = resolver.getResources(rootUrl + "**/*.properties");
        assertEquals(2, matches.length);
        Set<String> contents = new HashSet<>();
        for (Resource match : matches) {
            try (InputStream stream = match.getInputStream()) {
                contents.add(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(Set.of("one", "two"), contents);
    }
}
