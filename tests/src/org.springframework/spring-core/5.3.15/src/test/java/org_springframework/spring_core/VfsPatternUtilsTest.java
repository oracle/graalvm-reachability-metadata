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
    void findsMatchingResourcesInVirtualFileTree() throws Exception {
        Path directFile = temporaryDirectory.resolve("direct.txt");
        Path nestedFile = temporaryDirectory.resolve("nested").resolve("nested.txt");
        Files.createDirectories(nestedFile.getParent());
        Files.write(directFile, "direct".getBytes(UTF_8));
        Files.write(nestedFile, "nested".getBytes(UTF_8));
        Files.write(temporaryDirectory.resolve("ignored.dat"), new byte[] {1});
        VirtualFile directory = VFS.getChild(temporaryDirectory.toUri());
        URL directoryUrl = directory.asDirectoryURL();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(directoryUrl.toExternalForm() + "**/*.txt");

        assertThat(resources).extracting(Resource::getFilename).containsExactlyInAnyOrder("direct.txt", "nested.txt");
    }
}
