/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_ssh_external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.wagon.CommandExecutionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.Streams;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.repository.RepositoryPermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
public class ScpExternalWagonTest {
    // Local transport executables record argv and perform real shell commands and file copies.
    private static final String OPTIONS = """
            while [ "$#" -gt 0 ]; do
                case "$1" in
                    -i|-o|-p|-P|-pw) shift 2 ;;
                    -batch|-q) shift ;;
                    *) break ;;
                esac
            done
            """;

    @TempDir
    Path temporaryDirectory;

    private final ScpExternalWagon wagon = new ScpExternalWagon();
    private Path repositoryDirectory;
    private Path sshExecutable;
    private Path scpExecutable;
    private AuthenticationInfo authentication;

    @BeforeEach
    void connectLocalTransport() throws Exception {
        repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        sshExecutable = executable("local-ssh", """
                printf '%s\\n' "$@" > "$(dirname "$0")/ssh-arguments"
                """ + OPTIONS + """
                [ "$#" -eq 2 ] || exit 255
                exec sh -c "$2"
                """);
        scpExecutable = executable("local-scp", """
                printf '%s\\n' "$@" > "$(dirname "$0")/scp-arguments"
                """ + OPTIONS + """
                [ "$#" -eq 2 ] || exit 2
                source=$1
                destination=$2
                case "$source" in *@*:*) source=${source#*:} ;; esac
                case "$destination" in *@*:*) destination=${destination#*:} ;; esac
                if [ ! -f "$source" ]; then
                    printf 'No such file or directory\\n' >&2
                    exit 1
                fi
                exec cp "$source" "$destination"
                """);
        wagon.setSshExecutable(sshExecutable.toString());
        wagon.setScpExecutable(scpExecutable.toString());
        wagon.setInteractive(false);
        authentication = new AuthenticationInfo();
        authentication.setUserName("deployer");
        authentication.setPassword("transport-password");
        Repository repository = new Repository("local", "scpexe://repository.test" + repositoryDirectory);
        repository.setPort(2222);
        wagon.connect(repository, authentication);
    }

    @AfterEach
    void disconnect() throws Exception {
        wagon.disconnect();
    }

    @Test
    void executesCommandsAndPreservesArgumentsAndBothOutputStreams() throws Exception {
        Path privateKey = Files.writeString(temporaryDirectory.resolve("private key"), "fixture key");
        authentication.setPassword(null);
        authentication.setPrivateKey(privateKey.toString());
        wagon.setSshArgs("-o 'ConnectTimeout 10'");
        String command = "printf 'command output\\n'; printf 'command diagnostic\\n' >&2";

        Streams streams = wagon.executeCommand(command, false);

        assertThat(streams.getOut()).isEqualTo("command output\n");
        assertThat(streams.getErr()).isEqualTo("command diagnostic\n");
        assertThat(Files.readAllLines(temporaryDirectory.resolve("ssh-arguments"))).containsExactly(
                "-i", privateKey.toString(), "-o", "BatchMode yes", "-p", "2222",
                "-o", "ConnectTimeout 10", "deployer@repository.test", command);
        assertThat(wagon.getSshExecutable()).isEqualTo(sshExecutable.toString());
        assertThat(wagon.getSshArgs()).isEqualTo("-o 'ConnectTimeout 10'");

        Path marker = temporaryDirectory.resolve("command-marker");
        wagon.executeCommand("printf completed > '" + marker + "'");
        assertThat(Files.readString(marker)).isEqualTo("completed");
    }

    @Test
    void reportsCommandFailuresAndOnlyIgnoresNonfatalExitCodesWhenRequested() throws Exception {
        String command = "printf 'partial output\\n'; printf 'command refused\\n' >&2; exit 7";

        assertThatThrownBy(() -> wagon.executeCommand(command))
                .isInstanceOf(CommandExecutionException.class)
                .hasMessageContaining("Exit code 7")
                .hasMessageContaining("command refused");
        Streams ignored = wagon.executeCommand(command, true);
        assertThat(ignored.getOut()).isEqualTo("partial output\n");
        assertThat(ignored.getErr()).isEqualTo("command refused\n");
        assertThatThrownBy(() -> wagon.executeCommand("printf 'connection refused\\n' >&2; exit 255", true))
                .isInstanceOf(CommandExecutionException.class)
                .hasMessageContaining("Exit code 255")
                .hasMessageContaining("connection refused");
    }

    @Test
    void uploadsAndDownloadsFilesUsingRepositoryPathsAndPermissions() throws Exception {
        byte[] payload = new byte[] {0, 1, 2, 10, 13, 32, 127, -1};
        Path source = Files.write(temporaryDirectory.resolve("local payload.bin"), payload);
        RepositoryPermissions permissions = new RepositoryPermissions();
        permissions.setDirectoryMode("750");
        permissions.setFileMode("640");
        wagon.getRepository().setPermissions(permissions);
        wagon.setScpArgs("-q");

        wagon.put(source.toFile(), "nested/payload.bin");

        Path uploaded = repositoryDirectory.resolve("nested/payload.bin");
        assertThat(Files.readAllBytes(uploaded)).containsExactly(payload);
        assertThat(Files.getPosixFilePermissions(uploaded)).isEqualTo(Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ));
        assertThat(Files.readAllLines(temporaryDirectory.resolve("scp-arguments"))).containsExactly(
                "-o", "BatchMode yes", "-P", "2222", "-q", source.getFileName().toString(),
                "deployer@repository.test:" + uploaded);
        assertThat(wagon.getScpExecutable()).isEqualTo(scpExecutable.toString());
        assertThat(wagon.getScpArgs()).isEqualTo("-q");

        Path destination = temporaryDirectory.resolve("downloads/nested/downloaded payload.bin");
        wagon.get("nested\\payload.bin", destination.toFile());

        assertThat(Files.readAllBytes(destination)).containsExactly(payload);
        assertThat(Files.readAllLines(temporaryDirectory.resolve("scp-arguments"))).containsExactly(
                "-o", "BatchMode yes", "-P", "2222", "-q",
                "deployer@repository.test:" + uploaded, destination.getFileName().toString());
    }

    @Test
    void uploadsDirectoryContentsAndRemovesTheTransferArchive() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("site"));
        Files.writeString(source.resolve("index.html"), "<h1>Published site</h1>");
        Path assets = Files.createDirectory(source.resolve("assets"));
        Files.writeString(assets.resolve("style.css"), "body { color: navy; }");

        wagon.putDirectory(source.toFile(), "published");

        Path published = repositoryDirectory.resolve("published");
        assertThat(Files.readString(published.resolve("index.html"))).isEqualTo("<h1>Published site</h1>");
        assertThat(Files.readString(published.resolve("assets/style.css"))).isEqualTo("body { color: navy; }");
        try (Stream<Path> paths = Files.walk(published)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".zip"))).isEmpty();
        }
    }

    @Test
    void configuresPuttyPasswordBatchAndPortOptionsForBothExecutables() throws Exception {
        Path plink = Files.copy(sshExecutable, temporaryDirectory.resolve("local-plink"));
        Path pscp = Files.copy(scpExecutable, temporaryDirectory.resolve("local-pscp"));
        wagon.setSshExecutable(plink.toString());
        wagon.setScpExecutable(pscp.toString());
        String command = "printf 'putty output\\n'";

        assertThat(wagon.executeCommand(command, false).getOut()).isEqualTo("putty output\n");
        assertThat(Files.readAllLines(temporaryDirectory.resolve("ssh-arguments"))).containsExactly(
                "-pw", "transport-password", "-batch", "-P", "2222", "deployer@repository.test", command);

        Path source = Files.writeString(temporaryDirectory.resolve("putty.txt"), "putty transfer");
        wagon.put(source.toFile(), "putty.txt");

        assertThat(Files.readString(repositoryDirectory.resolve("putty.txt"))).isEqualTo("putty transfer");
        assertThat(Files.readAllLines(temporaryDirectory.resolve("scp-arguments"))).containsExactly(
                "-pw", "transport-password", "-batch", "-P", "2222", "putty.txt",
                "deployer@repository.test:" + repositoryDirectory.resolve("putty.txt"));
    }

    @Test
    void reportsMissingFilesAndExecutablesThroughWagonExceptions() throws Exception {
        Path missing = temporaryDirectory.resolve("absent.bin");
        assertThatThrownBy(() -> wagon.put(missing.toFile(), "absent.bin"))
                .isInstanceOf(TransferFailedException.class)
                .hasMessageContaining("source file does not exist");
        assertThatThrownBy(() -> wagon.get("absent.bin", temporaryDirectory.resolve("download.bin").toFile()))
                .isInstanceOf(ResourceDoesNotExistException.class)
                .hasMessageContaining("No such file or directory");

        wagon.setSshExecutable(temporaryDirectory.resolve("absent-ssh").toString());
        assertThatThrownBy(() -> wagon.executeCommand("printf unused"))
                .isInstanceOf(CommandExecutionException.class)
                .hasMessageContaining("Error executing command line");

        wagon.setScpExecutable(temporaryDirectory.resolve("absent-scp").toString());
        assertThatThrownBy(() -> wagon.get("file.bin", temporaryDirectory.resolve("download.bin").toFile()))
                .isInstanceOf(TransferFailedException.class)
                .hasMessageContaining("Error executing command line");
    }

    private Path executable(String name, String body) throws IOException {
        Path script = Files.writeString(temporaryDirectory.resolve(name), "#!/bin/sh\n" + body);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }
}
