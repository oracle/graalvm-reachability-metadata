/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Verifies wildcard resource discovery through Spring's JBoss VFS visitor adapter. */
public class VfsPatternUtilsTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void findsTextResourcesInVirtualFileTree() throws Exception {
        Path nestedDirectory = Files.createDirectories(temporaryDirectory.resolve("nested"));
        Files.writeString(temporaryDirectory.resolve("direct.txt"), "direct", UTF_8);
        Files.writeString(nestedDirectory.resolve("nested.txt"), "nested", UTF_8);
        Files.writeString(nestedDirectory.resolve("ignored.dat"), "ignored", UTF_8);
        VirtualFile root = VFS.getChild(temporaryDirectory.toUri());
        URL rootUrl = root.asDirectoryURL();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(rootUrl.toExternalForm() + "**/*.txt");

        assertThat(rootUrl.getProtocol()).isEqualTo("vfs");
        assertThat(resources)
                .extracting(Resource::getFilename)
                .containsExactlyInAnyOrder("direct.txt", "nested.txt");
    }
}
