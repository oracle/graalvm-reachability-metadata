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
import org.apache.maven.scm.log.DefaultLog;
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
}
