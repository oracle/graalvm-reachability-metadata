/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_ssh_external;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.wagon.CommandExecutionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.Streams;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.repository.RepositoryPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Wagon_ssh_externalTest {
    private static final String REMOTE_HOST = "repository.example.test";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(10)
    void settersExposeExternalExecutablesAndArguments() {
        ScpExternalWagon wagon = new ScpExternalWagon();

        assertThat(wagon.getScpExecutable()).isEqualTo("scp");
        assertThat(wagon.getSshExecutable()).isEqualTo("ssh");
        assertThat(wagon.getScpArgs()).isNull();
        assertThat(wagon.getSshArgs()).isNull();
        assertThat(wagon.supportsDirectoryCopy()).isTrue();

        wagon.setScpExecutable("/usr/local/bin/custom-scp");
        wagon.setSshExecutable("/usr/local/bin/custom-ssh");
        wagon.setScpArgs("-C -q");
        wagon.setSshArgs("-v -T");

        assertThat(wagon.getScpExecutable()).isEqualTo("/usr/local/bin/custom-scp");
        assertThat(wagon.getSshExecutable()).isEqualTo("/usr/local/bin/custom-ssh");
        assertThat(wagon.getScpArgs()).isEqualTo("-C -q");
        assertThat(wagon.getSshArgs()).isEqualTo("-v -T");
    }

    @Test
    @Timeout(10)
    void executeCommandBuildsOpenSshCommandLineAndCapturesStreams() throws Exception {
        Path log = temporaryDirectory.resolve("ssh.log");
        Path ssh = writeScript("fake-ssh", """
            #!/bin/sh
            {
              echo 'CALL:ssh'
              echo "PWD:$(pwd)"
              for arg in "$@"; do
                printf 'ARG:%%s\n' "$arg"
              done
            } >> %s
            printf 'remote-output\n'
            printf 'remote-error\n' >&2
            exit 0
            """.formatted(shellQuote(log)));
        ScpExternalWagon wagon = newConnectedWagon(ssh);
        wagon.setSshArgs("-vv -T");

        try {
            Streams streams = wagon.executeCommand("echo wagon", false);

            assertThat(streams.getOut()).isEqualTo("remote-output\n");
            assertThat(streams.getErr()).isEqualTo("remote-error\n");
            assertThat(readLines(log)).containsSubsequence(
                "CALL:ssh",
                "ARG:-o",
                "ARG:BatchMode yes",
                "ARG:-p",
                "ARG:2222",
                "ARG:-vv",
                "ARG:-T",
                "ARG:deployer@" + REMOTE_HOST,
                "ARG:echo wagon"
            );
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    @Timeout(10)
    void executeCommandUsesPuttyBatchPasswordAndPortOptionsWhenExecutableLooksLikePlink() throws Exception {
        Path log = temporaryDirectory.resolve("plink.log");
        Path plink = writeScript("plink", """
            #!/bin/sh
            {
              echo 'CALL:plink'
              for arg in "$@"; do
                printf 'ARG:%%s\n' "$arg"
              done
            } >> %s
            exit 0
            """.formatted(shellQuote(log)));
        ScpExternalWagon wagon = newConnectedWagon(plink);

        try {
            wagon.executeCommand("whoami", false);

            assertThat(readLines(log)).containsSubsequence(
                "CALL:plink",
                "ARG:-pw",
                "ARG:secret",
                "ARG:-batch",
                "ARG:-P",
                "ARG:2222",
                "ARG:deployer@" + REMOTE_HOST,
                "ARG:whoami"
            );
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    @Timeout(10)
    void executeCommandHonorsIgnoredNonFatalFailuresButRejectsFatalSshFailures() throws Exception {
        Path nonFatalSsh = writeScript("non-fatal-ssh", """
            #!/bin/sh
            printf 'partial-output\n'
            printf 'non-fatal-error\n' >&2
            exit 7
            """);
        ScpExternalWagon nonFatalWagon = newConnectedWagon(nonFatalSsh);
        try {
            Streams streams = nonFatalWagon.executeCommand("status", true);

            assertThat(streams.getOut()).isEqualTo("partial-output\n");
            assertThat(streams.getErr()).isEqualTo("non-fatal-error\n");
        } finally {
            nonFatalWagon.disconnect();
        }

        Path fatalSsh = writeScript("fatal-ssh", """
            #!/bin/sh
            printf 'fatal-error\n' >&2
            exit 255
            """);
        ScpExternalWagon fatalWagon = newConnectedWagon(fatalSsh);
        try {
            assertThatExceptionOfType(CommandExecutionException.class)
                .isThrownBy(() -> fatalWagon.executeCommand("status", true))
                .withMessageContaining("Exit code 255")
                .withMessageContaining("fatal-error");
        } finally {
            fatalWagon.disconnect();
        }
    }

    @Test
    @Timeout(10)
    void putCreatesRemoteDirectoryTransfersFileAndAppliesRepositoryPermissions() throws Exception {
        Path log = temporaryDirectory.resolve("transfer.log");
        Path ssh = loggingScript("fake-ssh", "ssh", log, 0);
        Path scp = loggingScript("fake-scp", "scp", log, 0);
        Path source = temporaryDirectory.resolve("artifact.jar");
        Files.writeString(source, "artifact contents", StandardCharsets.UTF_8);
        Repository repository = repositoryWithPermissions();
        ScpExternalWagon wagon = new ScpExternalWagon();
        wagon.setSshExecutable(ssh.toString());
        wagon.setScpExecutable(scp.toString());
        wagon.setScpArgs("-C -q");
        wagon.connect(repository, authenticationInfo());

        try {
            wagon.put(source.toFile(), "com\\acme\\artifact.jar");

            String logContents = Files.readString(log, StandardCharsets.UTF_8);
            assertThat(logContents)
                .contains("CALL:ssh")
                .contains("ARG:umask 2; mkdir -p /var/maven/com/acme")
                .contains("ARG:chgrp -f maven /var/maven/com/acme/artifact.jar")
                .contains("ARG:chmod -f 640 /var/maven/com/acme/artifact.jar")
                .contains("CALL:scp")
                .contains("PWD:" + temporaryDirectory)
                .contains("ARG:-P")
                .contains("ARG:2222")
                .contains("ARG:-C")
                .contains("ARG:-q")
                .contains("ARG:artifact.jar")
                .contains("ARG:deployer@" + REMOTE_HOST + ":/var/maven/com/acme/artifact.jar");
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    @Timeout(10)
    void getCreatesDestinationParentsAndConvertsScpMissingFileToResourceException() throws Exception {
        Path log = temporaryDirectory.resolve("get.log");
        Path successfulScp = writeScript("get-scp", """
            #!/bin/sh
            last=''
            {
              echo 'CALL:scp'
              echo "PWD:$(pwd)"
              for arg in "$@"; do
                last="$arg"
                printf 'ARG:%%s\n' "$arg"
              done
            } >> %s
            : > "$last"
            exit 0
            """.formatted(shellQuote(log)));
        ScpExternalWagon wagon = new ScpExternalWagon();
        wagon.setScpExecutable(successfulScp.toString());
        wagon.connect(repository(), authenticationInfo());
        Path destination = temporaryDirectory.resolve("downloaded/nested/artifact.txt");

        try {
            wagon.get("remote\\artifact.txt", destination.toFile());

            assertThat(destination).exists().isRegularFile();
            assertThat(Files.readString(log, StandardCharsets.UTF_8))
                .contains("CALL:scp")
                .contains("PWD:" + destination.getParent())
                .contains("ARG:deployer@" + REMOTE_HOST + ":/var/maven/remote/artifact.txt")
                .contains("ARG:artifact.txt");
        } finally {
            wagon.disconnect();
        }

        Path missingScp = writeScript("missing-scp", """
            #!/bin/sh
            printf 'scp: /var/maven/missing.txt: No such file or directory\n' >&2
            exit 1
            """);
        ScpExternalWagon missingWagon = new ScpExternalWagon();
        missingWagon.setScpExecutable(missingScp.toString());
        missingWagon.connect(repository(), authenticationInfo());
        try {
            File missingDestination = temporaryDirectory.resolve("missing/missing.txt").toFile();
            assertThatExceptionOfType(ResourceDoesNotExistException.class)
                .isThrownBy(() -> missingWagon.get("missing.txt", missingDestination))
                .withMessageContaining("No such file or directory");
        } finally {
            missingWagon.disconnect();
        }
    }

    @Test
    @Timeout(10)
    void inheritedDirectoryQueriesUseSshCommandExecutor() throws Exception {
        Path ssh = writeScript("listing-ssh", """
            #!/bin/sh
            command=''
            for arg in "$@"; do
              command="$arg"
            done
            case "$command" in
              *missing*)
                printf 'ls: missing: No such file or directory\n' >&2
                exit 1
                ;;
              'ls -la '* )
                cat <<'EOF'
            drwxr-xr-x 2 user group 4096 Jan 1 00:00 .
            -rw-r--r-- 1 user group   42 Jan 1 00:00 artifact.jar
            drwxr-xr-x 2 user group 4096 Jan 1 00:00 nested
            EOF
                exit 0
                ;;
              *)
                exit 0
                ;;
            esac
            """);
        ScpExternalWagon wagon = newConnectedWagon(ssh);

        try {
            List<String> files = wagon.getFileList("releases");

            assertThat(files).contains("artifact.jar", "nested");
            assertThat(wagon.resourceExists("releases/artifact.jar")).isTrue();
            assertThat(wagon.resourceExists("missing/artifact.jar")).isFalse();
        } finally {
            wagon.disconnect();
        }
    }

    private static Path loggingScript(String name, String marker, Path log, int exitCode) throws Exception {
        return writeExecutable(log.getParent().resolve(name), """
            #!/bin/sh
            {
              echo 'CALL:%s'
              echo "PWD:$(pwd)"
              for arg in "$@"; do
                printf 'ARG:%%s\n' "$arg"
              done
            } >> %s
            exit %d
            """.formatted(marker, shellQuote(log), exitCode));
    }

    private Path writeScript(String name, String content) throws Exception {
        return writeExecutable(temporaryDirectory.resolve(name), content);
    }

    private static Path writeExecutable(Path script, String content) throws Exception {
        Files.writeString(script, content.stripIndent(), StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private static ScpExternalWagon newConnectedWagon(Path sshExecutable) throws Exception {
        ScpExternalWagon wagon = new ScpExternalWagon();
        wagon.setSshExecutable(sshExecutable.toString());
        wagon.connect(repository(), authenticationInfo());
        return wagon;
    }

    private static Repository repositoryWithPermissions() {
        Repository repository = repository();
        RepositoryPermissions permissions = new RepositoryPermissions();
        permissions.setDirectoryMode("775");
        permissions.setFileMode("640");
        permissions.setGroup("maven");
        repository.setPermissions(permissions);
        return repository;
    }

    private static Repository repository() {
        return new Repository("test-repository", "scpexe://" + REMOTE_HOST + ":2222/var/maven");
    }

    private static AuthenticationInfo authenticationInfo() {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUserName("deployer");
        authenticationInfo.setPassword("secret");
        return authenticationInfo;
    }

    private static List<String> readLines(Path path) throws Exception {
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    private static String shellQuote(Path path) {
        return "'" + path.toString().replace("'", "'\"'\"'") + "'";
    }
}
