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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

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
        repository.setPushChanges(false);
        ScmTag startTag = new ScmTag("v1.0.0");
        ScmTag endTag = new ScmTag("v1.1.0");

        Commandline cachedDiffCommandLine = GitDiffCommand.createCommandLine(workingDirectory, startTag, endTag, true);
        assertGitExecutable(cachedDiffCommandLine);
        assertThat(cachedDiffCommandLine.getArguments())
                .containsExactly("diff", "--cached", "v1.0.0", "v1.1.0");

        Commandline rawDiffCommandLine = GitDiffCommand.createDiffRawCommandLine(workingDirectory, "HEAD");
        assertThat(rawDiffCommandLine.getArguments()).containsExactly("diff", "--raw", "HEAD");

        initializeGitRepository(workingDirectory);
        GitTagCommand tagCommand = new GitTagCommand(Map.of());
        ScmTagParameters defaultTagParameters = new ScmTagParameters("default tag message");
        defaultTagParameters.setSignOption(SignOption.DEFAULT);
        ScmResult defaultTagResult = tagCommand.executeTagCommand(
                repository, new ScmFileSet(workingDirectory), "v1.1.0", defaultTagParameters);
        assertThat(defaultTagResult.isSuccess()).isTrue();
        assertThat(defaultTagResult.getCommandLine()).contains("tag").contains("v1.1.0");

        ScmTagParameters unsignedTagParameters = new ScmTagParameters("unsigned tag message");
        unsignedTagParameters.setSignOption(SignOption.FORCE_NO_SIGN);
        ScmResult unsignedTagResult = tagCommand.executeTagCommand(
                repository, new ScmFileSet(workingDirectory), "v1.1.1", unsignedTagParameters);
        assertThat(unsignedTagResult.isSuccess()).isTrue();
        assertThat(unsignedTagResult.getCommandLine()).contains("--no-sign").contains("v1.1.1");

        Commandline tagPushCommandLine = tagCommand.createPushCommandLine(
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

    private static void assertGitExecutable(Commandline commandLine) {
        assertThat(commandLine.getExecutable()).isIn("git", "'git'");
    }

    private static void initializeGitRepository(File workingDirectory) throws Exception {
        runGitCommand(workingDirectory, "init");
        runGitCommand(workingDirectory, "config", "user.email", "native-test@example.invalid");
        runGitCommand(workingDirectory, "config", "user.name", "Native Test");
        runGitCommand(workingDirectory, "add", "tracked.txt");
        runGitCommand(workingDirectory, "commit", "-m", "initial commit");
    }

    private static void runGitCommand(File workingDirectory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("Timed out running: " + command);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("git command output:%n%s", output).isZero();
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
