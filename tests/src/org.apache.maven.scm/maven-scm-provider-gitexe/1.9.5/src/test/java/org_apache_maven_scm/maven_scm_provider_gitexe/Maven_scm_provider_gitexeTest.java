/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_scm.maven_scm_provider_gitexe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.maven.scm.ChangeFile;
import org.apache.maven.scm.ChangeSet;
import org.apache.maven.scm.CommandParameter;
import org.apache.maven.scm.CommandParameters;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.command.blame.BlameLine;
import org.apache.maven.scm.command.info.InfoItem;
import org.apache.maven.scm.command.remoteinfo.RemoteInfoScmResult;
import org.apache.maven.scm.log.ScmLogger;
import org.apache.maven.scm.provider.git.gitexe.GitExeScmProvider;
import org.apache.maven.scm.provider.git.gitexe.command.GitCommandLineUtils;
import org.apache.maven.scm.provider.git.gitexe.command.add.GitAddCommand;
import org.apache.maven.scm.provider.git.gitexe.command.blame.GitBlameConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.branch.GitBranchCommand;
import org.apache.maven.scm.provider.git.gitexe.command.branch.GitCurrentBranchConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.changelog.GitChangeLogCommand;
import org.apache.maven.scm.provider.git.gitexe.command.changelog.GitChangeLogConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.checkin.GitCheckInCommand;
import org.apache.maven.scm.provider.git.gitexe.command.checkout.GitCheckOutCommand;
import org.apache.maven.scm.provider.git.gitexe.command.diff.GitDiffCommand;
import org.apache.maven.scm.provider.git.gitexe.command.diff.GitDiffRawConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.info.GitInfoCommand;
import org.apache.maven.scm.provider.git.gitexe.command.info.GitInfoConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.list.GitListCommand;
import org.apache.maven.scm.provider.git.gitexe.command.list.GitListConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.remoteinfo.GitRemoteInfoCommand;
import org.apache.maven.scm.provider.git.gitexe.command.remoteinfo.GitRemoteInfoConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.remove.GitRemoveCommand;
import org.apache.maven.scm.provider.git.gitexe.command.remove.GitRemoveConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.status.GitStatusCommand;
import org.apache.maven.scm.provider.git.gitexe.command.status.GitStatusConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.tag.GitTagCommand;
import org.apache.maven.scm.provider.git.gitexe.command.update.GitLatestRevisionCommandConsumer;
import org.apache.maven.scm.provider.git.gitexe.command.update.GitUpdateCommand;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.codehaus.plexus.util.cli.Commandline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_scm_provider_gitexeTest {
    private static final ScmLogger LOGGER = new NoOpScmLogger();

    @TempDir
    Path temporaryDirectory;

    @Test
    void statusConsumerParsesPorcelainOutputAndFiltersNonFiles() throws Exception {
        Files.write(temporaryDirectory.resolve("added.txt"), Collections.singletonList("new"));
        Files.write(temporaryDirectory.resolve("modified.txt"), Collections.singletonList("changed"));
        Files.write(temporaryDirectory.resolve("renamed.txt"), Collections.singletonList("renamed"));
        Files.write(temporaryDirectory.resolve("with space.txt"), Collections.singletonList("space"));

        GitStatusConsumer consumer = new GitStatusConsumer(LOGGER, temporaryDirectory.toFile());
        consumer.consumeLine("A  added.txt");
        consumer.consumeLine(" M modified.txt");
        consumer.consumeLine(" D deleted.txt");
        consumer.consumeLine("R  old-name.txt -> renamed.txt");
        consumer.consumeLine("A  \"with space.txt\"");
        consumer.consumeLine("?? ignored-untracked.txt");

        assertThat(consumer.getChangedFiles())
                .extracting(ScmFile::getPath, ScmFile::getStatus)
                .containsExactly(
                        tuple("added.txt", ScmFileStatus.ADDED),
                        tuple("modified.txt", ScmFileStatus.MODIFIED),
                        tuple("deleted.txt", ScmFileStatus.DELETED),
                        tuple("old-name.txt", ScmFileStatus.RENAMED),
                        tuple("renamed.txt", ScmFileStatus.RENAMED),
                        tuple("with space.txt", ScmFileStatus.ADDED));
    }

    @Test
    void statusConsumerResolvesRepositoryRelativePaths() {
        URI modulePath = URI.create("module-a/src/");

        URI relative = GitStatusConsumer.resolveURI("module-a/src/main/App.java", modulePath);
        URI quotedWithSpaces = GitStatusConsumer.resolveURI("\"module-a/src/test data/App Test.java\"", modulePath);

        assertThat(relative.getPath()).isEqualTo("main/App.java");
        assertThat(quotedWithSpaces.getPath()).isEqualTo("test data/App Test.java");
    }

    @Test
    void diffRawConsumerParsesAddedUpdatedAndDeletedEntries() {
        GitDiffRawConsumer consumer = new GitDiffRawConsumer(LOGGER);

        consumer.consumeLine(":000000 100644 0000000 e69de29 A\tnew.txt");
        consumer.consumeLine(":100644 100644 e69de29 0123456 M\tmodified.txt");
        consumer.consumeLine(":100644 000000 e69de29 0000000 D\tdeleted.txt");
        consumer.consumeLine("malformed line");
        consumer.consumeLine(":100644 100644 e69de29 0123456 T\tunknown.txt");

        assertThat(consumer.getChangedFiles())
                .extracting(ScmFile::getPath, ScmFile::getStatus)
                .containsExactly(
                        tuple("new.txt", ScmFileStatus.ADDED),
                        tuple("modified.txt", ScmFileStatus.UPDATED),
                        tuple("deleted.txt", ScmFileStatus.DELETED));
    }

    @Test
    void blameConsumerReusesCommitMetadataForRepeatedPorcelainEntries() {
        GitBlameConsumer consumer = new GitBlameConsumer(LOGGER);

        consumer.consumeLine("0123456789abcdef 1 1 1");
        consumer.consumeLine("author Alice Example");
        consumer.consumeLine("committer Bob Committer");
        consumer.consumeLine("committer-time 1700000000");
        consumer.consumeLine("\tfirst line");
        consumer.consumeLine("0123456789abcdef 2 2 1");
        consumer.consumeLine("\tsecond line");

        assertThat(consumer.getLines())
                .extracting(BlameLine::getRevision, BlameLine::getAuthor, BlameLine::getCommitter)
                .containsExactly(
                        tuple("0123456789abcdef", "Alice Example", "Bob Committer"),
                        tuple("0123456789abcdef", "Alice Example", "Bob Committer"));
        assertThat(consumer.getLines().get(0).getDate()).isEqualTo(new Date(1700000000L * 1000L));
    }

    @Test
    void changeLogConsumerParsesRawCommitWithParentsAndRename() {
        GitChangeLogConsumer consumer = new GitChangeLogConsumer(LOGGER, null);

        consumer.consumeLine("commit abcdef1234567890");
        consumer.consumeLine("tree 1111111111111111111111111111111111111111");
        consumer.consumeLine("parent 2222222222222222222222222222222222222222");
        consumer.consumeLine("parent 3333333333333333333333333333333333333333");
        consumer.consumeLine("author Alice Example <alice@example.test> 1700000000 +0000");
        consumer.consumeLine("committer Bob Committer <bob@example.test> 1700000001 +0000");
        consumer.consumeLine("");
        consumer.consumeLine("    first comment line");
        consumer.consumeLine("    second comment line");
        consumer.consumeLine("");
        consumer.consumeLine(":100644 100644 aaaaaaa bbbbbbb M\tmodified.txt");
        consumer.consumeLine(":100644 100644 ccccccc ddddddd R100\told.txt\tnew.txt");

        List<ChangeSet> changes = consumer.getModifications();

        assertThat(changes).hasSize(1);
        ChangeSet change = changes.get(0);
        assertThat(change.getRevision()).isEqualTo("abcdef1234567890");
        assertThat(change.getParentRevision()).isEqualTo("2222222222222222222222222222222222222222");
        assertThat(change.getMergedRevisions()).containsExactly("3333333333333333333333333333333333333333");
        assertThat(change.getAuthor()).isEqualTo("Alice Example <alice@example.test>");
        assertThat(change.getComment()).isEqualTo("first comment line\nsecond comment line");
        assertThat(change.getFiles())
                .extracting(ChangeFile::getName, ChangeFile::getAction, ChangeFile::getOriginalName)
                .containsExactly(
                        tuple("modified.txt", ScmFileStatus.MODIFIED, null),
                        tuple("new.txt", ScmFileStatus.RENAMED, "old.txt"));
    }

    @Test
    void remoteInfoConsumerCollectsBranchesAndTags() {
        GitRemoteInfoConsumer consumer = new GitRemoteInfoConsumer(LOGGER, "git ls-remote origin");

        consumer.consumeLine("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\trefs/heads/master");
        consumer.consumeLine("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb refs/heads/feature/native");
        consumer.consumeLine("cccccccccccccccccccccccccccccccccccccccc\trefs/tags/v1.0.0");
        consumer.consumeLine("not a ref line");

        RemoteInfoScmResult result = consumer.getRemoteInfoScmResult();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCommandLine()).isEqualTo("git ls-remote origin");
        assertThat(result.getBranches())
                .containsEntry("master", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .containsEntry("feature/native", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(result.getTags()).containsEntry("v1.0.0", "cccccccccccccccccccccccccccccccccccccccc");
    }

    @Test
    void fileListAndRemoveConsumersKeepOnlyExistingFilesAndRemovedEntries() throws Exception {
        Files.write(temporaryDirectory.resolve("tracked.txt"), Collections.singletonList("tracked"));
        Files.createDirectories(temporaryDirectory.resolve("directory"));

        GitListConsumer listConsumer = new GitListConsumer(
                LOGGER, temporaryDirectory.toFile(), ScmFileStatus.CHECKED_IN);
        listConsumer.consumeLine("tracked.txt");
        listConsumer.consumeLine("directory");
        listConsumer.consumeLine("missing.txt");

        assertThat(listConsumer.getListedFiles())
                .extracting(ScmFile::getPath, ScmFile::getStatus)
                .containsExactly(tuple("tracked.txt", ScmFileStatus.CHECKED_IN));

        GitRemoveConsumer removeConsumer = new GitRemoveConsumer(LOGGER);
        removeConsumer.consumeLine("rm 'tracked.txt'");
        removeConsumer.consumeLine("rm 'directory/child.txt'");
        removeConsumer.consumeLine("unable to parse");

        assertThat(removeConsumer.getRemovedFiles())
                .extracting(ScmFile::getPath, ScmFile::getStatus)
                .containsExactly(
                        tuple("tracked.txt", ScmFileStatus.DELETED),
                        tuple("directory/child.txt", ScmFileStatus.DELETED));
    }

    @Test
    void simpleConsumersCaptureBranchRevisionAndInfo() {
        GitCurrentBranchConsumer branchConsumer = new GitCurrentBranchConsumer(LOGGER);
        branchConsumer.consumeLine("refs/tags/v1.0.0");
        branchConsumer.consumeLine(" refs/heads/main ");

        GitLatestRevisionCommandConsumer revisionConsumer = new GitLatestRevisionCommandConsumer(LOGGER);
        revisionConsumer.consumeLine("");
        revisionConsumer.consumeLine("Author: Nobody");
        revisionConsumer.consumeLine("commit 0123456789abcdef");

        GitInfoConsumer infoConsumer = new GitInfoConsumer(LOGGER, new ScmFileSet(temporaryDirectory.toFile()));
        infoConsumer.consumeLine("  abcdef1  ");
        infoConsumer.consumeLine("ignored second revision");

        assertThat(branchConsumer.getBranchName()).isEqualTo("main");
        assertThat(revisionConsumer.getLatestRevision()).isEqualTo("0123456789abcdef");
        assertThat(infoConsumer.getInfoItems())
                .extracting(InfoItem::getRevision, InfoItem::getURL)
                .containsExactly(tuple("abcdef1", temporaryDirectory.toFile().getPath()));
    }

    @Test
    void providerExposesGitInfoCommandImplementation() {
        GitExeScmProvider provider = new GitExeScmProvider();

        assertThat(provider.getInfoCommand()).isInstanceOf(GitInfoCommand.class);
    }

    @Test
    void commandLineUtilitiesBuildRelativeTargetsAndAnonymousGitCommands() throws Exception {
        Path nestedDirectory = Files.createDirectories(temporaryDirectory.resolve("nested"));
        File tracked = Files.write(
                temporaryDirectory.resolve("tracked.txt"), Collections.singletonList("tracked")).toFile();
        File nested = Files.write(nestedDirectory.resolve("file.txt"), Collections.singletonList("nested")).toFile();
        Commandline commandline = GitCommandLineUtils.getBaseGitCommandLine(temporaryDirectory.toFile(), "custom");

        GitCommandLineUtils.addTarget(commandline, Arrays.asList(tracked, nested));

        assertThat(commandline.getExecutable()).isNotEmpty();
        assertThat(commandline.getWorkingDirectory()).isEqualTo(temporaryDirectory.toFile().getAbsoluteFile());
        assertThat(arguments(commandline))
                .containsExactly("custom", "tracked.txt", "nested" + File.separator + "file.txt");
        assertThat(GitCommandLineUtils.getBaseGitCommandLine(temporaryDirectory.toFile(), "")).isNull();
    }

    @Test
    void staticCommandFactoriesCreateExpectedCommandArguments() throws Exception {
        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.test/project.git");
        ScmFileSet fileSet = new ScmFileSet(temporaryDirectory.toFile());
        File file = Files.write(temporaryDirectory.resolve("file.txt"), Collections.singletonList("content")).toFile();
        Path directory = Files.createDirectory(temporaryDirectory.resolve("directory"));
        File messageFile = Files.write(
                temporaryDirectory.resolve("message.txt"), Collections.singletonList("message")).toFile();

        assertThat(arguments(GitAddCommand.createCommandLine(
                temporaryDirectory.toFile(), Collections.singletonList(file))))
                .containsExactly("add", "--", "file.txt");
        assertThat(arguments(GitRemoveCommand.createCommandLine(
                temporaryDirectory.toFile(), Collections.singletonList(directory.toFile()))))
                .containsExactly("rm", "-r", "directory");
        assertThat(arguments(GitStatusCommand.createCommandLine(repository, fileSet)))
                .containsExactly("status", "--porcelain", ".");
        assertThat(arguments(GitStatusCommand.createRevparseShowToplevelCommand(fileSet)))
                .containsExactly("rev-parse", "--show-toplevel");
        assertThat(arguments(GitBranchCommand.createCommandLine(repository, temporaryDirectory.toFile(), "feature")))
                .containsExactly("branch", "feature");
        assertThat(arguments(GitBranchCommand.createPushCommandLine(repository, fileSet, "feature")))
                .containsExactly("push", repository.getPushUrl(), "refs/heads/feature");
        assertThat(arguments(GitTagCommand.createCommandLine(
                repository, temporaryDirectory.toFile(), "v1.0.0", messageFile)))
                .containsExactly("tag", "-F", messageFile.getAbsolutePath(), "v1.0.0");
        assertThat(arguments(GitTagCommand.createPushCommandLine(repository, fileSet, "v1.0.0")))
                .containsExactly("push", repository.getPushUrl(), "refs/tags/v1.0.0");
    }

    @Test
    void revisionCommandFactoriesIncludeRequestedVersionsAndLimits() throws Exception {
        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.test/project.git");
        ScmFileSet fileSet = new ScmFileSet(temporaryDirectory.toFile());
        File messageFile = Files.write(
                temporaryDirectory.resolve("message.txt"), Collections.singletonList("message")).toFile();
        File changedFile = Files.write(
                temporaryDirectory.resolve("changed.txt"), Collections.singletonList("changed")).toFile();
        CommandParameters parameters = new CommandParameters();
        parameters.setInt(CommandParameter.SCM_SHORT_REVISION_LENGTH, 8);

        assertThat(arguments(GitCheckOutCommand.createCommandLine(
                repository, temporaryDirectory.toFile(), new ScmBranch("develop"))))
                .containsExactly("checkout", "develop");
        assertThat(arguments(GitDiffCommand.createCommandLine(
                temporaryDirectory.toFile(), new ScmRevision("abc"), new ScmRevision("def"), true)))
                .containsExactly("diff", "--cached", "abc", "def");
        assertThat(arguments(GitDiffCommand.createDiffRawCommandLine(temporaryDirectory.toFile(), "abc")))
                .containsExactly("diff", "--raw", "abc");
        assertThat(arguments(GitUpdateCommand.createCommandLine(
                repository, temporaryDirectory.toFile(), new ScmBranch("develop"))))
                .containsExactly("pull", repository.getFetchUrl(), "develop");
        assertThat(arguments(GitUpdateCommand.createLatestRevisionCommandLine(
                repository, temporaryDirectory.toFile(), new ScmBranch("develop"))))
                .containsExactly("log", "-n1", "--date-order", "develop");
        assertThat(arguments(GitCheckInCommand.createCommitCommandLine(
                repository, new ScmFileSet(temporaryDirectory.toFile()), messageFile)))
                .contains("commit", "--verbose", "-F", messageFile.getAbsolutePath(), "-a");
        assertThat(arguments(GitCheckInCommand.createCommitCommandLine(
                repository, new ScmFileSet(temporaryDirectory.toFile(), changedFile), messageFile)))
                .contains("commit", "--verbose", "-F", messageFile.getAbsolutePath(), "changed.txt")
                .doesNotContain("-a");
        assertThat(arguments(GitInfoCommand.createCommandLine(repository, fileSet, parameters)))
                .containsExactly("rev-parse", "--verify", "--short=8", "HEAD");
    }

    @Test
    void changeLogCommandFactoryBuildsDateBranchAndRevisionRanges() throws Exception {
        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.test/project.git");
        Date startDate = new Date(0L);
        Date endDate = new Date(3_600_000L);

        Commandline commandline = GitChangeLogCommand.createCommandLine(
                repository,
                temporaryDirectory.toFile(),
                new ScmBranch("release"),
                startDate,
                endDate,
                new ScmRevision("start"),
                new ScmTag("end"));

        assertThat(arguments(commandline))
                .contains("whatchanged", "--date=iso", "start..end", "release", "--");
        assertThat(arguments(commandline))
                .anySatisfy(argument -> assertThat(argument).startsWith("--since="))
                .anySatisfy(argument -> assertThat(argument).startsWith("--until="));
    }

    @Test
    void listAndRemoteInfoCommandFactoriesUseExpectedGitSubcommands() throws Exception {
        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.test/project.git");

        assertThat(arguments(GitListCommand.createCommandLine(repository, temporaryDirectory.toFile())))
                .containsExactly("ls-files");
        assertThat(arguments(GitRemoteInfoCommand.createCommandLine(repository)))
                .containsExactly("ls-remote", repository.getPushUrl());
    }

    private static List<String> arguments(Commandline commandline) {
        return Arrays.asList(commandline.getArguments());
    }

    private static final class NoOpScmLogger implements ScmLogger {
        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String content) {
        }

        @Override
        public void debug(String content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public void info(String content) {
        }

        @Override
        public void info(String content, Throwable error) {
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(String content) {
        }

        @Override
        public void warn(String content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(String content) {
        }

        @Override
        public void error(String content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }
    }
}
