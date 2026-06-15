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

public class ReflogWriterTest {

    @TempDir
    Path repositoryDirectory;

    @Test
    void commitWritesBranchReflogWhenEnabled() throws Exception {
        try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                    ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, "always");
            config.save();

            Files.writeString(repositoryDirectory.resolve("file.txt"), "content\n",
                    StandardCharsets.UTF_8);
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("Initial commit").call();

            String branch = git.getRepository().getBranch();
            Path branchLog = repositoryDirectory.resolve(".git/logs/refs/heads")
                    .resolve(branch);
            assertThat(Files.isRegularFile(branchLog)).isTrue();
            assertThat(Files.readString(branchLog, StandardCharsets.UTF_8))
                    .contains("Initial commit");
        }
    }
}
