/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FileRepositoryTest {

    @TempDir
    Path repositoryDirectory;

    @Test
    void initializesFileRepositoryWithDefaultCoreOptions() throws Exception {
        try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
            assertThat(git.getRepository().isBare()).isFalse();
            assertThat(Files.isDirectory(repositoryDirectory.resolve(".git"))).isTrue();
        }
    }
}
