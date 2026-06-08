/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_scm.maven_scm_provider_gitexe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.command.remoteinfo.RemoteInfoScmResult;
import org.apache.maven.scm.log.DefaultLog;
import org.apache.maven.scm.provider.git.gitexe.command.remoteinfo.GitRemoteInfoConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.status.GitStatusConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class Maven_scm_provider_gitexeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void statusConsumerParsesPorcelainStatusOutputIntoScmFiles() throws Exception {
        Path addedPath = temporaryDirectory.resolve("added.txt");
        Path modifiedPath = temporaryDirectory.resolve("modified.txt");
        Path renamedPath = temporaryDirectory.resolve("renamed.txt");
        Path spacedPath = temporaryDirectory.resolve("space name.txt");
        Files.writeString(addedPath, "added\n", StandardCharsets.UTF_8);
        Files.writeString(modifiedPath, "modified\n", StandardCharsets.UTF_8);
        Files.writeString(renamedPath, "renamed\n", StandardCharsets.UTF_8);
        Files.writeString(spacedPath, "spaced\n", StandardCharsets.UTF_8);

        GitStatusConsumer consumer = new GitStatusConsumer(new DefaultLog(), temporaryDirectory.toFile());
        consumer.consumeLine("A  added.txt");
        consumer.consumeLine(" M modified.txt");
        consumer.consumeLine(" D deleted.txt");
        consumer.consumeLine("R  old-name.txt -> renamed.txt");
        consumer.consumeLine(" M \"space name.txt\"");
        consumer.consumeLine("?? ignored-by-maven-scm.txt");

        assertThat(consumer.getChangedFiles())
                .extracting(ScmFile::getPath, ScmFile::getStatus)
                .containsExactly(
                        tuple("added.txt", ScmFileStatus.ADDED),
                        tuple("modified.txt", ScmFileStatus.MODIFIED),
                        tuple("deleted.txt", ScmFileStatus.DELETED),
                        tuple("old-name.txt", ScmFileStatus.RENAMED),
                        tuple("renamed.txt", ScmFileStatus.RENAMED),
                        tuple("space name.txt", ScmFileStatus.MODIFIED));
    }

    @Test
    void remoteInfoConsumerParsesBranchesAndTagsFromLsRemoteOutput() {
        GitRemoteInfoConsumer consumer = new GitRemoteInfoConsumer(new DefaultLog(), "git ls-remote origin");
        consumer.consumeLine("1111111111111111111111111111111111111111\trefs/heads/main");
        consumer.consumeLine("2222222222222222222222222222222222222222 refs/heads/feature/native-tests");
        consumer.consumeLine("3333333333333333333333333333333333333333\trefs/tags/v1.0.0");
        consumer.consumeLine("4444444444444444444444444444444444444444 refs/pull/7/head");

        RemoteInfoScmResult result = consumer.getRemoteInfoScmResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCommandLine()).isEqualTo("git ls-remote origin");
        assertThat(result.getBranches())
                .containsEntry("main", "1111111111111111111111111111111111111111")
                .containsEntry("feature/native-tests", "2222222222222222222222222222222222222222")
                .hasSize(2);
        assertThat(result.getTags())
                .containsEntry("v1.0.0", "3333333333333333333333333333333333333333")
                .hasSize(1);
    }
}
