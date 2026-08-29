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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Verifies VFS path matching through Spring's resource pattern API. */
public class VfsPatternUtilsTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void visitsVirtualFilesMatchingPattern() throws Exception {
        Path nested = temporaryDirectory.resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("match.txt"), "spring", UTF_8);
        Files.writeString(nested.resolve("ignore.dat"), "ignored", UTF_8);
        String root = VFS.getChild(temporaryDirectory.toUri()).toURL().toExternalForm();
        String separator = root.endsWith("/") ? "" : "/";

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(root + separator + "**/*.txt");

        assertThat(resources).singleElement().extracting(Resource::getFilename).isEqualTo("match.txt");
    }
}
