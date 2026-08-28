/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Verifies VFS tree traversal through Spring's public resource pattern resolver. */
public class VfsPatternUtilsTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void findsMatchingResourcesInVirtualFileTree() throws Exception {
        Path nestedDirectory = Files.createDirectories(this.temporaryDirectory.resolve("nested"));
        Files.writeString(this.temporaryDirectory.resolve("root.txt"), "root", UTF_8);
        Files.writeString(nestedDirectory.resolve("nested.txt"), "nested", UTF_8);
        Files.writeString(nestedDirectory.resolve("ignored.bin"), "ignored", UTF_8);
        VirtualFile root = VFS.getChild(this.temporaryDirectory.toUri());
        String rootLocation = root.toURL().toExternalForm();
        if (!rootLocation.endsWith("/")) {
            rootLocation += "/";
        }

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(rootLocation + "**/*.txt");

        assertThat(resources)
                .extracting(Resource::getFilename)
                .containsExactlyInAnyOrder("root.txt", "nested.txt");
    }
}
