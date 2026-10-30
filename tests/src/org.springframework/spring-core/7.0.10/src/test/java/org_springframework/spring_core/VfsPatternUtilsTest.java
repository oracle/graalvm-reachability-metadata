/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.jboss.vfs.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.VfsResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Verifies wildcard resolution visits real JBoss VFS resources. */
public class VfsPatternUtilsTest {
    @TempDir
    private Path directory;

    @Test
    void matchesVirtualFilesThroughVisitorProxy() throws Exception {
        Files.writeString(directory.resolve("selected.properties"), "enabled=true", UTF_8);
        Files.writeString(directory.resolve("ignored.txt"), "ignored", UTF_8);
        String root = VFS.getChild(directory.toUri()).toURL().toExternalForm();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        resourceLoader.addProtocolResolver((location, loader) -> location.startsWith("vfs:")
                ? new VfsResource(VFS.getChild(URI.create(location))) : null);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);

        Resource[] resources = resolver.getResources(root + (root.endsWith("/") ? "" : "/") + "*.properties");

        assertThat(Arrays.stream(resources).map(Resource::getFilename)).containsExactly("selected.properties");
        assertThat(resources[0].getContentAsString(UTF_8)).isEqualTo("enabled=true");
    }
}
