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

/** Verifies recursive resource pattern matching through JBoss VFS. */
public class VfsPatternUtilsTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void discoversMatchingVirtualFilesRecursively() throws Exception {
        Path directFile = temporaryDirectory.resolve("direct.txt");
        Path nestedFile = temporaryDirectory.resolve("nested").resolve("nested.txt");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(directFile, "direct", UTF_8);
        Files.writeString(nestedFile, "nested", UTF_8);
        Files.writeString(temporaryDirectory.resolve("ignored.json"), "{}", UTF_8);
        VirtualFile root = VFS.getChild(temporaryDirectory.toUri());
        String locationPattern = root.asDirectoryURL().toExternalForm() + "**/*.txt";
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(locationPattern);

        assertThat(resources)
                .extracting(Resource::getFilename)
                .containsExactlyInAnyOrder("direct.txt", "nested.txt");
    }
}
