/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_scm.maven_scm_provider_git_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.log.DefaultLog;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.git.AbstractGitScmProvider;
import org.apache.maven.scm.provider.git.GitCommandUtils;
import org.apache.maven.scm.provider.git.GitConfigFileReader;
import org.apache.maven.scm.provider.git.command.GitCommand;
import org.apache.maven.scm.provider.git.command.diff.GitDiffConsumer;
import org.apache.maven.scm.provider.git.command.info.GitInfoItem;
import org.apache.maven.scm.provider.git.command.info.GitInfoScmResult;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.apache.maven.scm.provider.git.repository.RepositoryUrl;
import org.apache.maven.scm.provider.git.util.GitUtil;
import org.apache.maven.scm.providers.gitlib.settings.Settings;
import org.apache.maven.scm.providers.gitlib.settings.io.xpp3.GitXpp3Reader;
import org.apache.maven.scm.providers.gitlib.settings.io.xpp3.GitXpp3Writer;
import org.codehaus.plexus.util.cli.Commandline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_scm_provider_git_commonsTest {
    @AfterEach
    void restoreGitSettingsDirectory() {
        GitUtil.setSettingsDirectory(GitUtil.DEFAULT_SETTINGS_DIRECTORY);
    }

    @Test
    void parsesFetchAndPushRepositoryUrlsWithCredentials() throws Exception {
        GitScmProviderRepository repository = new GitScmProviderRepository(
                "[fetch=]https://reader@example.org/team/repo.git"
                        + "[push=]ssh://writer:secret@git.example.org:2222/team/repo.git");

        RepositoryUrl fetchInfo = repository.getFetchInfo();
        assertThat(fetchInfo.getProtocol()).isEqualTo("https");
        assertThat(fetchInfo.getUserName()).isEqualTo("reader");
        assertThat(fetchInfo.getHost()).isEqualTo("example.org");
        assertThat(fetchInfo.getPath()).isEqualTo("/team/repo.git");
        assertThat(repository.getFetchUrl()).isEqualTo("https://writer:secret@example.org/team/repo.git");

        RepositoryUrl pushInfo = repository.getPushInfo();
        assertThat(pushInfo.getProtocol()).isEqualTo("ssh");
        assertThat(pushInfo.getUserName()).isEqualTo("writer");
        assertThat(pushInfo.getPassword()).isEqualTo("secret");
        assertThat(pushInfo.getHost()).isEqualTo("git.example.org");
        assertThat(pushInfo.getPort()).isEqualTo("2222");
        assertThat(repository.getHost()).isEqualTo("git.example.org");
        assertThat(repository.getPort()).isEqualTo(2222);
        assertThat(repository.getPushUrl()).isEqualTo("ssh://writer:secret@git.example.org:2222/team/repo.git");
        assertThat(repository.toString()).isEqualTo(
                "[fetch=]https://writer:secret@example.org/team/repo.git"
                        + "[push=]ssh://writer:secret@git.example.org:2222/team/repo.git");
    }

    @Test
    void supportsScpLikeUrlsFileUrlsOverridesAndRelativePaths() throws Exception {
        GitScmProviderRepository scpLike = new GitScmProviderRepository("git@example.org:apache/project.git");
        assertThat(scpLike.getFetchInfo().getProtocol()).isEmpty();
        assertThat(scpLike.getFetchInfo().getUserName()).isEqualTo("git");
        assertThat(scpLike.getFetchInfo().getHost()).isEqualTo("example.org");
        assertThat(scpLike.getFetchInfo().getPath()).isEqualTo(":apache/project.git");
        assertThat(scpLike.getFetchUrl()).isEqualTo("git@example.org:apache/project.git");

        GitScmProviderRepository fileRepository = new GitScmProviderRepository("file:///var/git/local-repo.git");
        assertThat(fileRepository.getFetchInfo().getProtocol()).isEqualTo("file");
        assertThat(fileRepository.getFetchInfo().getHost()).isEmpty();
        assertThat(fileRepository.getFetchInfo().getPath()).isEqualTo("/var/git/local-repo.git");
        assertThat(fileRepository.getFetchUrl()).isEqualTo("file:///var/git/local-repo.git");

        GitScmProviderRepository authenticated = new GitScmProviderRepository(
                "https://original:ignored@example.org/team/repo.git", "user name", "p@ss word");
        assertThat(authenticated.getFetchUrl()).isEqualTo("https://user+name:p%40ss+word@example.org/team/repo.git");

        GitScmProviderRepository ancestor = new GitScmProviderRepository("https://example.org/team");
        GitScmProviderRepository child = new GitScmProviderRepository("https://example.org/team/sub/repo.git");
        assertThat(child.getRelativePath(ancestor)).isEqualTo("sub/repo.git");
        assertThat(ancestor.getRelativePath(child)).isNull();
    }

    @Test
    void readsGitConfigPropertiesByGroup(@TempDir Path temporaryDirectory) throws Exception {
        Path gitDirectory = temporaryDirectory.resolve(".git");
        Files.createDirectories(gitDirectory);
        Files.writeString(
                gitDirectory.resolve("config"),
                """
                # file comments are ignored
                [core]
                    repositoryformatversion = 0

                [remote \"origin\"]
                    url = https://example.org/apache/project.git
                    # property comments are ignored
                    fetch = +refs/heads/*:refs/remotes/origin/*
                    malformed line without equals
                [branch \"main\"]
                    remote = origin
                """,
                StandardCharsets.UTF_8);

        GitConfigFileReader reader = new GitConfigFileReader();
        reader.setConfigDirectory(gitDirectory.toFile());

        assertThat(reader.getConfigDirectory()).isEqualTo(gitDirectory.toFile());
        assertThat(reader.getProperty("remote \"origin\"", "url")).isEqualTo("https://example.org/apache/project.git");
        assertThat(reader.getProperty("remote \"origin\"", "fetch"))
                .isEqualTo("+refs/heads/*:refs/remotes/origin/*");
        assertThat(reader.getProperty("branch \"main\"", "remote")).isEqualTo("origin");
        assertThat(reader.getProperty("remote \"origin\"", "remote")).isNull();
        assertThat(reader.getProperty("missing", "url")).isNull();
    }

    @Test
    void parsesDiffOutputIntoChangedFilesDifferencesAndPatch(@TempDir Path temporaryDirectory) {
        GitDiffConsumer consumer = new GitDiffConsumer(new DefaultLog(), temporaryDirectory.toFile());

        String patch = """
                diff --git a/src/main/App.java b/src/main/App.java
                index 1111111..2222222 100644
                --- a/src/main/App.java
                +++ b/src/main/App.java
                @@ -1,2 +1,2 @@
                 package example;
                -class App {}
                +public class App {}
                \\ No newline at end of file
                diff --git a/README.md b/README.md
                new file mode 100644
                index 0000000..3333333
                --- /dev/null
                +++ b/README.md
                @@ -0,0 +1 @@
                +# Project
                """;
        patch.lines().forEach(consumer::consumeLine);

        assertThat(consumer.getChangedFiles()).hasSize(2);
        assertThat(consumer.getChangedFiles()).extracting(ScmFile::getPath)
                .containsExactly("src/main/App.java", "README.md");
        assertThat(consumer.getChangedFiles()).extracting(ScmFile::getStatus)
                .containsExactly(ScmFileStatus.MODIFIED, ScmFileStatus.MODIFIED);
        assertThat(consumer.getDifferences()).containsOnlyKeys("src/main/App.java", "README.md");
        assertThat(consumer.getDifferences().get("src/main/App.java").toString()).isEqualTo(
                """
                @@ -1,2 +1,2 @@
                 package example;
                -class App {}
                +public class App {}
                \\ No newline at end of file
                """);
        assertThat(consumer.getDifferences().get("README.md").toString()).isEqualTo(
                """
                @@ -0,0 +1 @@
                +# Project
                """);
        assertThat(consumer.getPatch()).contains("diff --git a/src/main/App.java b/src/main/App.java\n")
                .contains("new file mode 100644\n")
                .endsWith("+# Project\n");
    }

    @Test
    void readsWritesSettingsAndBuildsBaseGitCommand(@TempDir Path temporaryDirectory) throws Exception {
        Settings settings = new Settings();
        settings.setRevParseDateFormat("yyyyMMdd-HHmmss");
        settings.setTraceGitCommand("true");
        settings.setGitCommand("custom-git");
        settings.setCommitNoVerify(true);

        StringWriter settingsXml = new StringWriter();
        new GitXpp3Writer().write(settingsXml, settings);
        assertThat(settingsXml.toString()).contains("<revParseDateFormat>yyyyMMdd-HHmmss</revParseDateFormat>")
                .contains("<traceGitCommand>true</traceGitCommand>")
                .contains("<gitCommand>custom-git</gitCommand>")
                .contains("<commitNoVerify>true</commitNoVerify>");

        GitXpp3Reader reader = new GitXpp3Reader();
        reader.setAddDefaultEntities(false);
        Settings parsed = reader.read(new StringReader(settingsXml.toString()));
        assertThat(reader.getAddDefaultEntities()).isFalse();
        assertThat(parsed.getRevParseDateFormat()).isEqualTo("yyyyMMdd-HHmmss");
        assertThat(parsed.getTraceGitCommand()).isEqualTo("true");
        assertThat(parsed.getGitCommand()).isEqualTo("custom-git");
        assertThat(parsed.isCommitNoVerify()).isTrue();

        Path settingsDirectory = temporaryDirectory.resolve("settings");
        Files.createDirectories(settingsDirectory);
        Files.writeString(
                settingsDirectory.resolve("git-settings.xml"), settingsXml.toString(), StandardCharsets.UTF_8);
        GitUtil.setSettingsDirectory(settingsDirectory.toFile());

        Path workTree = temporaryDirectory.resolve("work-tree");
        Files.createDirectories(workTree);
        GitScmProviderRepository repository = new GitScmProviderRepository("https://example.org/team/repo.git");
        Commandline commandLine = GitCommandUtils.getBaseCommand(
                "status", repository, new ScmFileSet(workTree.toFile()), "--no-pager");

        assertThat(GitCommandUtils.getRevParseDateFormat()).isEqualTo("yyyyMMdd-HHmmss");
        assertThat(commandLine.getCommandline()[0]).isEqualTo("custom-git");
        assertThat(commandLine.getWorkingDirectory().getCanonicalFile())
                .isEqualTo(workTree.toFile().getCanonicalFile());
        assertThat(commandLine.getArguments()).containsExactly("--no-pager", "status");
        assertThat(commandLine.getEnvironmentVariables()).contains("GIT_TRACE=true");
    }

    @Test
    void createsInfoResultsFromItemsAndExistingScmResults() {
        GitInfoItem item = new GitInfoItem();
        item.setPath("src/main/App.java");
        item.setURL("https://example.org/team/repo/src/main/App.java");
        item.setRepositoryRoot("https://example.org/team/repo");
        item.setRevision("abc123");
        item.setLastChangedAuthor("alice");
        item.setLastChangedRevision("abc123");
        item.setLastChangedDate("2024-01-02T03:04:05Z");

        GitInfoScmResult success = new GitInfoScmResult("git info", List.of(item));
        assertThat(success.isSuccess()).isTrue();
        assertThat(success.getCommandLine()).isEqualTo("git info");
        assertThat(success.getInfoItems()).containsExactly(item);
        assertThat(success.getInfoItems().get(0).getPath()).isEqualTo("src/main/App.java");
        assertThat(success.getInfoItems().get(0).getURL())
                .isEqualTo("https://example.org/team/repo/src/main/App.java");

        GitInfoScmResult failure = new GitInfoScmResult("git info", "fatal", "not a repository", false);
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.getProviderMessage()).isEqualTo("fatal");
        assertThat(failure.getCommandOutput()).isEqualTo("not a repository");
        assertThat(failure.getInfoItems()).isEmpty();
    }

    @Test
    void abstractProviderCreatesRepositoriesFromUrlsAndGitCheckouts(@TempDir Path temporaryDirectory) throws Exception {
        MinimalGitScmProvider provider = new MinimalGitScmProvider();
        assertThat(provider.getScmType()).isEqualTo("git");
        assertThat(provider.getScmSpecificFilename()).isEqualTo(".git");
        assertThat(provider.validateScmUrl("https://example.org/team/repo.git", ':')).isEmpty();

        ScmProviderRepository fromUrl = provider.makeProviderScmRepository("https://example.org/team/repo.git", ':');
        assertThat(fromUrl).isInstanceOf(GitScmProviderRepository.class);
        assertThat(((GitScmProviderRepository) fromUrl).getFetchUrl()).isEqualTo("https://example.org/team/repo.git");

        Path checkout = temporaryDirectory.resolve("checkout");
        Path gitDirectory = checkout.resolve(".git");
        Files.createDirectories(gitDirectory);
        Files.writeString(
                gitDirectory.resolve("config"),
                """
                [remote \"origin\"]
                    url = ssh://git@example.org/team/checkout.git
                """,
                StandardCharsets.UTF_8);

        ScmProviderRepository fromCheckout = provider.makeProviderScmRepository(checkout.toFile());
        assertThat(fromCheckout).isInstanceOf(GitScmProviderRepository.class);
        assertThat(((GitScmProviderRepository) fromCheckout).getFetchUrl())
                .isEqualTo("ssh://git@example.org/team/checkout.git");
    }

    private static final class MinimalGitScmProvider extends AbstractGitScmProvider {
        @Override
        protected String getRepositoryURL(File path) throws ScmException {
            GitConfigFileReader reader = new GitConfigFileReader();
            reader.setConfigDirectory(new File(path, ".git"));
            String url = reader.getProperty("remote \"origin\"", "url");
            if (url == null) {
                throw new ScmException("No origin URL configured");
            }
            return url;
        }

        @Override
        protected GitCommand getAddCommand() {
            return null;
        }

        @Override
        protected GitCommand getBranchCommand() {
            return null;
        }

        @Override
        protected GitCommand getChangeLogCommand() {
            return null;
        }

        @Override
        protected GitCommand getCheckInCommand() {
            return null;
        }

        @Override
        protected GitCommand getCheckOutCommand() {
            return null;
        }

        @Override
        protected GitCommand getDiffCommand() {
            return null;
        }

        @Override
        protected GitCommand getExportCommand() {
            return null;
        }

        @Override
        protected GitCommand getRemoveCommand() {
            return null;
        }

        @Override
        protected GitCommand getStatusCommand() {
            return null;
        }

        @Override
        protected GitCommand getTagCommand() {
            return null;
        }

        @Override
        protected GitCommand getUpdateCommand() {
            return null;
        }

        @Override
        protected GitCommand getListCommand() {
            return null;
        }

        @Override
        protected GitCommand getInfoCommand() {
            return null;
        }

        @Override
        protected GitCommand getBlameCommand() {
            return null;
        }

        @Override
        protected GitCommand getRemoteInfoCommand() {
            return null;
        }
    }
}
