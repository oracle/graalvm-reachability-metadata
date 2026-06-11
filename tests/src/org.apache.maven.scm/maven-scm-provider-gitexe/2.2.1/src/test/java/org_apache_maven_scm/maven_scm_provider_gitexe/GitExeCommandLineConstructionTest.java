/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_scm.maven_scm_provider_gitexe;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.maven.scm.CommandParameter;
import org.apache.maven.scm.CommandParameters;
import org.apache.maven.scm.CommandParameters.SignOption;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.ScmTagParameters;
import org.apache.maven.scm.command.info.InfoItem;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.git.gitexe.command.checkin.GitCheckInCommand;
import org.apache.maven.scm.provider.git.gitexe.command.checkout.GitCheckOutCommand;
import org.apache.maven.scm.provider.git.gitexe.command.diff.GitDiffCommand;
import org.apache.maven.scm.provider.git.gitexe.command.info.GitInfoCommand;
import org.apache.maven.scm.provider.git.gitexe.command.remoteinfo.GitRemoteInfoCommand;
import org.apache.maven.scm.provider.git.gitexe.command.remove.GitRemoveCommand;
import org.apache.maven.scm.provider.git.gitexe.command.tag.GitTagCommand;
import org.apache.maven.scm.provider.git.gitexe.command.update.GitUpdateCommand;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.codehaus.plexus.util.cli.Commandline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class GitExeCommandLineConstructionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void checkoutUpdateRemoteAndInfoFactoriesBuildExpectedGitArguments() throws Exception {
        File workingDirectory = temporaryDirectory.toFile();
        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.invalid/team/project.git");
        ScmBranch featureBranch = new ScmBranch("feature/native-tests");

        Commandline checkoutCommandLine =
                GitCheckOutCommand.createCommandLine(repository, workingDirectory, featureBranch);
        assertGitExecutable(checkoutCommandLine);
        assertThat(checkoutCommandLine.getWorkingDirectory()).isEqualTo(workingDirectory);
        assertThat(checkoutCommandLine.getArguments()).containsExactly("checkout", "feature/native-tests");

        Commandline updateCommandLine = GitUpdateCommand.createCommandLine(repository, workingDirectory, featureBranch);
        assertGitExecutable(updateCommandLine);
        assertThat(updateCommandLine.getWorkingDirectory()).isEqualTo(workingDirectory);
        assertThat(updateCommandLine.getArguments())
                .containsExactly("pull", "https://example.invalid/team/project.git", "feature/native-tests");

        Commandline latestRevisionCommandLine =
                GitUpdateCommand.createLatestRevisionCommandLine(repository, workingDirectory, featureBranch);
        assertThat(latestRevisionCommandLine.getArguments())
                .containsExactly("log", "-n1", "--date-order", "feature/native-tests");

        Commandline remoteInfoCommandLine = new GitRemoteInfoCommand(Map.of()).createCommandLine(repository);
        assertGitExecutable(remoteInfoCommandLine);
        File temporaryRoot = new File(System.getProperty("java.io.tmpdir"));
        assertThat(remoteInfoCommandLine.getWorkingDirectory()).isEqualTo(temporaryRoot);
        assertThat(remoteInfoCommandLine.getArguments())
                .containsExactly("ls-remote", "https://example.invalid/team/project.git");

        CommandParameters infoParameters = new CommandParameters();
        infoParameters.setInt(CommandParameter.SCM_SHORT_REVISION_LENGTH, 12);
        Commandline infoCommandLine = new CapturingGitInfoCommand()
                .createInfoCommandLine(repository, new ScmFileSet(workingDirectory), infoParameters);
        assertGitExecutable(infoCommandLine);
        assertThat(infoCommandLine.getWorkingDirectory()).isEqualTo(workingDirectory);
        assertThat(infoCommandLine.getArguments())
                .containsExactly("log", "-1", "--no-merges", "--format=format:%H %aI %aE %aN");
    }

    @Test
    void diffTagCommitAndRemoveFactoriesBuildExpectedArguments() throws Exception {
        File workingDirectory = temporaryDirectory.toFile();
        Path trackedPath = temporaryDirectory.resolve("tracked.txt");
        Path messagePath = temporaryDirectory.resolve("message.txt");
        Path removableDirectory = temporaryDirectory.resolve("directory-to-remove");
        Files.writeString(trackedPath, "content\n", StandardCharsets.UTF_8);
        Files.writeString(messagePath, "message\n", StandardCharsets.UTF_8);
        Files.createDirectories(removableDirectory);

        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.invalid/team/project.git");
        ScmTag startTag = new ScmTag("v1.0.0");
        ScmTag endTag = new ScmTag("v1.1.0");

        Commandline cachedDiffCommandLine = GitDiffCommand.createCommandLine(workingDirectory, startTag, endTag, true);
        assertGitExecutable(cachedDiffCommandLine);
        assertThat(cachedDiffCommandLine.getArguments())
                .containsExactly("diff", "--cached", "v1.0.0", "v1.1.0");

        Commandline rawDiffCommandLine = GitDiffCommand.createDiffRawCommandLine(workingDirectory, "HEAD");
        assertThat(rawDiffCommandLine.getArguments()).containsExactly("diff", "--raw", "HEAD");

        ScmResult tagResult = executeTag(repository, workingDirectory, "v1.1.0", SignOption.DEFAULT);
        assertThat(tagResult.isSuccess()).isFalse();
        assertThat(commandLineTokens(tagResult))
                .contains("tag", "-F", "v1.1.0")
                .doesNotContain("-s", "--no-sign");

        ScmResult signedTagResult = executeTag(repository, workingDirectory, "v1.1.0", SignOption.FORCE_SIGN);
        assertThat(signedTagResult.isSuccess()).isFalse();
        assertThat(commandLineTokens(signedTagResult))
                .contains("tag", "-s", "-F", "v1.1.0");

        ScmResult unsignedTagResult = executeTag(repository, workingDirectory, "v1.1.0", SignOption.FORCE_NO_SIGN);
        assertThat(unsignedTagResult.isSuccess()).isFalse();
        assertThat(commandLineTokens(unsignedTagResult))
                .contains("tag", "--no-sign", "-F", "v1.1.0");

        Commandline tagPushCommandLine = new GitTagCommand(Map.of()).createPushCommandLine(
                repository, new ScmFileSet(workingDirectory, trackedPath.toFile()), "v1.1.0");
        assertThat(tagPushCommandLine.getArguments())
                .containsExactly("push", "https://example.invalid/team/project.git", "refs/tags/v1.1.0");

        ScmFileSet trackedFileSet = new ScmFileSet(workingDirectory, trackedPath.toFile());
        Commandline commitCommandLine =
                GitCheckInCommand.createCommitCommandLine(repository, trackedFileSet, messagePath.toFile());
        assertThat(commitCommandLine.getArguments())
                .containsExactly("commit", "--verbose", "-F", messagePath.toAbsolutePath().toString());

        Commandline removeCommandLine = GitRemoveCommand.createCommandLine(
                workingDirectory, List.of(removableDirectory.toFile(), trackedPath.toFile()));
        assertThat(removeCommandLine.getArguments()).contains("rm", "-r", "directory-to-remove", "tracked.txt");
    }

    private static ScmResult executeTag(
            GitScmProviderRepository repository,
            File workingDirectory,
            String tag,
            SignOption signOption) throws ScmException {
        ScmTagParameters tagParameters = new ScmTagParameters("message");
        tagParameters.setSignOption(signOption);
        return new GitTagCommand(Map.of()).executeTagCommand(
                repository, new ScmFileSet(workingDirectory), tag, tagParameters);
    }

    private static List<String> commandLineTokens(ScmResult result) {
        return List.of(result.getCommandLine().split("\\s+")).stream()
                .map(GitExeCommandLineConstructionTest::removeShellQuotes)
                .toList();
    }

    private static String removeShellQuotes(String token) {
        if (token.length() >= 2 && token.startsWith("'") && token.endsWith("'")) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    private static void assertGitExecutable(Commandline commandLine) {
        assertThat(commandLine.getExecutable()).isIn("git", "'git'");
    }

    private static final class CapturingGitInfoCommand extends GitInfoCommand {
        private Commandline commandLine;

        Commandline createInfoCommandLine(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) throws ScmException {
            executeCommand(repository, fileSet, parameters);
            return commandLine;
        }

        @Override
        protected InfoItem executeInfoCommand(
                Commandline cli, CommandParameters parameters, File scmFile) {
            commandLine = cli;
            return new InfoItem();
        }
    }
}
