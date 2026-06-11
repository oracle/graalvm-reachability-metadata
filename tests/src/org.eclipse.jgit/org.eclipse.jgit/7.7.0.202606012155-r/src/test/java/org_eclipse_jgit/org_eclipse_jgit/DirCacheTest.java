/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DirCacheTest {

    @TempDir
    Path repositoryDirectory;

    @Test
    void addUsesConfiguredIndexVersion() throws Exception {
        try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setInt(ConfigConstants.CONFIG_INDEX_SECTION, null,
                    ConfigConstants.CONFIG_KEY_VERSION, 4);
            config.save();

            Files.writeString(repositoryDirectory.resolve("file.txt"), "content\n",
                    StandardCharsets.UTF_8);
            git.add().addFilepattern("file.txt").call();

            assertThat(Files.isRegularFile(repositoryDirectory.resolve(".git/index"))).isTrue();
        }
    }
}
