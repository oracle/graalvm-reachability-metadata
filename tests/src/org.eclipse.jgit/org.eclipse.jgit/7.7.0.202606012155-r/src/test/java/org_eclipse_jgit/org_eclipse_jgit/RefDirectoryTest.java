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

public class RefDirectoryTest {

    @TempDir
    Path repositoryDirectory;

    @Test
    void packsAndReadsRefsWithConfiguredPackedRefsStatTrust() throws Exception {
        try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
            Files.writeString(repositoryDirectory.resolve("file.txt"), "content\n",
                    StandardCharsets.UTF_8);
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("Initial commit").call();

            StoredConfig config = git.getRepository().getConfig();
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                    ConfigConstants.CONFIG_KEY_TRUST_PACKED_REFS_STAT, "after_open");
            config.save();
        }

        try (Git git = Git.open(repositoryDirectory.toFile())) {
            String result = git.packRefs().setAll(true).call();

            assertThat(result).isNotEmpty();
            assertThat(Files.isRegularFile(repositoryDirectory.resolve(".git/packed-refs")))
                    .isTrue();
            assertThat(git.branchList().call()).isNotEmpty();
        }
    }
}
