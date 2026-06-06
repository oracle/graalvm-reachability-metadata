/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.maven.wagon.Streams;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.providers.ssh.CommandExecutorStreamProcessor;
import org.apache.maven.wagon.providers.ssh.LSParser;
import org.apache.maven.wagon.providers.ssh.interactive.ConsoleInteractiveUserInfo;
import org.apache.maven.wagon.providers.ssh.interactive.NullInteractiveUserInfo;
import org.apache.maven.wagon.providers.ssh.jsch.AbstractJschWagon;
import org.apache.maven.wagon.providers.ssh.jsch.ScpWagon;
import org.apache.maven.wagon.providers.ssh.jsch.SftpWagon;
import org.apache.maven.wagon.providers.ssh.jsch.interactive.PrompterUIKeyboardInteractive;
import org.apache.maven.wagon.providers.ssh.jsch.interactive.UserInfoUIKeyboardInteractiveProxy;
import org.apache.maven.wagon.providers.ssh.knownhost.FileKnownHostsProvider;
import org.apache.maven.wagon.providers.ssh.knownhost.KnownHostsProvider;
import org.apache.maven.wagon.providers.ssh.knownhost.NullKnownHostProvider;
import org.apache.maven.wagon.providers.ssh.knownhost.SingleKnownHostProvider;
import org.apache.maven.wagon.providers.ssh.knownhost.StreamKnownHostsProvider;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
public class Wagon_sshTest {
    private static final String PREFERRED_AUTHENTICATIONS = "gssapi-with-mic,publickey,password,keyboard-interactive";

    @TempDir
    Path temporaryDirectory;

    @Test
    void scpAndSftpWagonsApplyConfigurationBeforeConnectionFailure() throws Exception {
        int port = findUnusedLocalPort();

        for (AbstractJschWagon wagon : List.of(new ScpWagon(), new SftpWagon())) {
            NullKnownHostProvider knownHostsProvider = new NullKnownHostProvider();
            NullInteractiveUserInfo interactiveUserInfo = new NullInteractiveUserInfo(true);
            wagon.setKnownHostsProvider(knownHostsProvider);
            wagon.setInteractiveUserInfo(interactiveUserInfo);
            wagon.setPreferredAuthentications(PREFERRED_AUTHENTICATIONS);
            wagon.setInteractive(false);

            assertThat(wagon.supportsDirectoryCopy()).isTrue();
            assertThat(wagon.getKnownHostsProvider()).isSameAs(knownHostsProvider);
            assertThat(wagon.getInteractiveUserInfo()).isSameAs(interactiveUserInfo);
            wagon.setKnownHostsProvider(null);
            assertThat(wagon.getKnownHostsProvider()).isNull();
            wagon.setKnownHostsProvider(knownHostsProvider);
            wagon.setInteractiveUserInfo(null);
            assertThat(wagon.getInteractiveUserInfo()).isNull();
            wagon.setInteractiveUserInfo(interactiveUserInfo);

            assertThatThrownBy(() -> wagon.connect(repository(port), authenticationInfo()))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("Cannot connect");
            assertThat(wagon.getRepository().getPort()).isEqualTo(port);
            assertThat(wagon.getAuthenticationInfo().getUserName()).isEqualTo("wagon-user");
            assertThat(wagon.getInteractiveUserInfo()).isInstanceOf(NullInteractiveUserInfo.class);

            wagon.disconnect();
        }
    }

    @Test
    void httpProxyConfigurationIsUsedWhenConnecting() throws Exception {
        try (ServerSocket proxyServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            proxyServer.setSoTimeout(5_000);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<List<String>> proxyRequest = executor.submit(() -> readHttpProxyRequest(proxyServer));
            ScpWagon wagon = new ScpWagon();
            ProxyInfo proxyInfo = new ProxyInfo();
            proxyInfo.setType(ProxyInfo.PROXY_HTTP);
            proxyInfo.setHost("127.0.0.1");
            proxyInfo.setPort(proxyServer.getLocalPort());
            proxyInfo.setUserName("proxy-user");
            proxyInfo.setPassword("proxy-password");

            try {
                wagon.setKnownHostsProvider(new NullKnownHostProvider());
                wagon.setPreferredAuthentications(PREFERRED_AUTHENTICATIONS);
                wagon.setInteractive(false);

                Repository proxiedRepository = new Repository("proxied", "scp://repository.example.test/repository");
                proxiedRepository.setPort(2222);

                assertThatThrownBy(() -> wagon.connect(proxiedRepository, authenticationInfo(), proxyInfo))
                        .isInstanceOf(AuthenticationException.class)
                        .hasMessageContaining("Cannot connect");

                List<String> requestLines = proxyRequest.get(5, TimeUnit.SECONDS);
                String authorizationHeader = "Proxy-Authorization: Basic " + Base64.getEncoder()
                        .encodeToString("proxy-user:proxy-password".getBytes(StandardCharsets.ISO_8859_1));
                assertThat(requestLines.get(0)).isEqualTo("CONNECT repository.example.test:2222 HTTP/1.0");
                assertThat(requestLines).contains(authorizationHeader);
            } finally {
                wagon.disconnect();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void knownHostsProvidersExposeAndPersistContents() throws Exception {
        KnownHostsProvider singleProvider = new SingleKnownHostProvider(
                "example.test", "AAAAB3NzaC1yc2EAAAADAQABAAABAQC");
        assertThat(singleProvider.getContents())
                .isEqualTo("example.test ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC\n");
        assertThat(singleProvider.getHostKeyChecking()).isEqualTo("ask");
        singleProvider.setHostKeyChecking("no");
        assertThat(singleProvider.getHostKeyChecking()).isEqualTo("no");

        String knownHosts = "server.test ssh-rsa AAAA\nother.test ssh-dss BBBB\n";
        StreamKnownHostsProvider streamProvider = new StreamKnownHostsProvider(
                new ByteArrayInputStream(knownHosts.getBytes(StandardCharsets.UTF_8)));
        assertThat(streamProvider.getContents()).isEqualTo(knownHosts);

        File knownHostsFile = temporaryDirectory.resolve("ssh/known_hosts").toFile();
        FileKnownHostsProvider emptyFileProvider = new FileKnownHostsProvider(knownHostsFile);
        assertThat(emptyFileProvider.getFile()).isEqualTo(knownHostsFile);
        assertThat(emptyFileProvider.getContents()).isEmpty();

        emptyFileProvider.storeKnownHosts(knownHosts);
        assertThat(Files.readString(knownHostsFile.toPath())).isEqualTo(knownHosts);
        FileKnownHostsProvider populatedFileProvider = new FileKnownHostsProvider(knownHostsFile);
        assertThat(populatedFileProvider.getContents()).isEqualTo(knownHosts);

        NullKnownHostProvider nullProvider = new NullKnownHostProvider();
        assertThat(nullProvider.getContents()).isNull();
        nullProvider.storeKnownHosts("ignored");
        assertThat(nullProvider.getContents()).isNull();
    }

    @Test
    void prompterKeyboardInteractiveUsesEchoFlagsAndHandlesFailures() throws Exception {
        RecordingPrompter prompter = new RecordingPrompter();
        PrompterUIKeyboardInteractive interactive = new PrompterUIKeyboardInteractive(prompter);

        String[] answers = interactive.promptKeyboardInteractive(
                "destination", "name", "instruction", new String[] {"login", "secret"}, new boolean[] {true, false});

        assertThat(answers).containsExactly("answer:login", "password:secret");
        assertThat(prompter.prompts).containsExactly("login");
        assertThat(prompter.passwordPrompts).containsExactly("secret");
        assertThatThrownBy(() -> interactive.promptKeyboardInteractive(
                "destination", "name", "instruction", new String[] {"one"}, new boolean[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");

        prompter.failOnPrompt = true;
        assertThat(interactive.promptKeyboardInteractive(
                "destination", "name", "instruction", new String[] {"login"}, new boolean[] {true}))
                .isNull();
    }

    @Test
    void consoleInteractiveUserInfoRoutesPromptsThroughPrompter() {
        ConsolePrompter prompter = new ConsolePrompter();
        ConsoleInteractiveUserInfo interactiveUserInfo = new ConsoleInteractiveUserInfo(prompter);

        prompter.promptReply = "yes";
        assertThat(interactiveUserInfo.promptYesNo("Trust host?"))
                .isTrue();
        prompter.promptReply = "NO";
        assertThat(interactiveUserInfo.promptYesNo("Trust second host?"))
                .isFalse();
        assertThat(prompter.prompts)
                .containsExactly("Trust host?", "Trust second host?");
        assertThat(prompter.possibleValues)
                .containsExactly(List.of("yes", "no"), List.of("yes", "no"));

        prompter.passwordReply = "secret";
        assertThat(interactiveUserInfo.promptPassword("Password:"))
                .isEqualTo("secret");
        prompter.passwordReply = "phrase";
        assertThat(interactiveUserInfo.promptPassphrase("Passphrase:"))
                .isEqualTo("phrase");
        assertThat(prompter.passwordPrompts)
                .containsExactly("Password:", "Passphrase:");

        interactiveUserInfo.showMessage("notice");
        assertThat(prompter.messages)
                .containsExactly("notice");

        prompter.failOnPrompt = true;
        assertThat(interactiveUserInfo.promptYesNo("Unavailable?"))
                .isFalse();
        prompter.failOnPasswordPrompt = true;
        assertThat(interactiveUserInfo.promptPassword("Unavailable password:"))
                .isNull();
        assertThat(interactiveUserInfo.promptPassphrase("Unavailable passphrase:"))
                .isNull();
        prompter.failOnShowMessage = true;
        interactiveUserInfo.showMessage("ignored");
    }

    @Test
    void userInfoKeyboardInteractiveProxyDelegatesBothInterfaces() {
        RecordingUserInfo userInfo = new RecordingUserInfo();
        UIKeyboardInteractive keyboardInteractive = (destination, name, instruction, prompt, echo) -> new String[] {
                destination, name, instruction, prompt[0], Boolean.toString(echo[0])
        };
        UserInfoUIKeyboardInteractiveProxy proxy = new UserInfoUIKeyboardInteractiveProxy(
                userInfo, keyboardInteractive);

        assertThat(proxy.getPassphrase()).isEqualTo("delegate-passphrase");
        assertThat(proxy.getPassword()).isEqualTo("delegate-password");
        assertThat(proxy.promptPassword("password?")).isTrue();
        assertThat(proxy.promptPassphrase("passphrase?")).isFalse();
        assertThat(proxy.promptYesNo("continue?")).isTrue();
        proxy.showMessage("hello");
        assertThat(userInfo.messages).containsExactly("hello");
        assertThat(proxy.promptKeyboardInteractive(
                "dest", "name", "instruction", new String[] {"prompt"}, new boolean[] {true}))
                .containsExactly(
                        "dest",
                        "name",
                        "instruction",
                        "Keyboard interactive required, supplied password is ignored\nprompt",
                        "true");
    }

    @Test
    void sshUtilityParsersCollectExpectedOutput() throws Exception {
        Streams streams = CommandExecutorStreamProcessor.processStreams(
                reader("Could not chdir to home directory /missing\nttyname: Operation not supported\nreal error\n"),
                reader("first line\nsecond line\n"));
        assertThat(streams.getErr()).isEqualTo("real error\n");
        assertThat(streams.getOut()).isEqualTo("first line\nsecond line\n");

        assertThat(new LSParser().parseFiles("total 8\n-rw-r--r-- 1 user group 12 Jan 01 12:34 artifact.jar\n"
                + "drwxr-xr-x 2 user group 96 Feb 02 2024 directory\ninvalid\n"))
                .containsExactly("artifact.jar", "directory");
    }

    private static BufferedReader reader(String content) {
        return new BufferedReader(new StringReader(content));
    }

    private static List<String> readHttpProxyRequest(ServerSocket serverSocket) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(5_000);
            BufferedReader requestReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            List<String> requestLines = new ArrayList<>();
            String line;
            while ((line = requestReader.readLine()) != null && !line.isEmpty()) {
                requestLines.add(line);
            }
            byte[] response = "HTTP/1.0 403 Forbidden\r\nContent-Length: 0\r\n\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1);
            socket.getOutputStream().write(response);
            socket.getOutputStream().flush();
            return requestLines;
        }
    }

    private static Repository repository(int port) {
        Repository repository = new Repository("test", "scp://127.0.0.1/repository");
        repository.setPort(port);
        return repository;
    }

    private static AuthenticationInfo authenticationInfo() {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUserName("wagon-user");
        authenticationInfo.setPassword("secret-password");
        return authenticationInfo;
    }

    private static int findUnusedLocalPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return serverSocket.getLocalPort();
        }
    }

    private static final class ConsolePrompter implements Prompter {
        private final List<String> prompts = new ArrayList<>();
        private final List<List<String>> possibleValues = new ArrayList<>();
        private final List<String> passwordPrompts = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private String promptReply = "yes";
        private String passwordReply = "secret";
        private boolean failOnPrompt;
        private boolean failOnPasswordPrompt;
        private boolean failOnShowMessage;

        @Override
        public String prompt(String message) throws PrompterException {
            if (failOnPrompt) {
                throw new PrompterException("failed");
            }
            prompts.add(message);
            return promptReply;
        }

        @Override
        public String prompt(String message, String defaultReply) throws PrompterException {
            return prompt(message);
        }

        @Override
        public String prompt(String message, List possibleValues) throws PrompterException {
            List<String> values = new ArrayList<>();
            for (Object possibleValue : possibleValues) {
                values.add(String.valueOf(possibleValue));
            }
            this.possibleValues.add(values);
            return prompt(message);
        }

        @Override
        public String prompt(String message, List possibleValues, String defaultReply) throws PrompterException {
            return prompt(message, possibleValues);
        }

        @Override
        public String promptForPassword(String message) throws PrompterException {
            if (failOnPasswordPrompt) {
                throw new PrompterException("failed");
            }
            passwordPrompts.add(message);
            return passwordReply;
        }

        @Override
        public void showMessage(String message) throws PrompterException {
            if (failOnShowMessage) {
                throw new PrompterException("failed");
            }
            messages.add(message);
        }
    }

    private static final class RecordingPrompter implements Prompter {
        private final List<String> prompts = new ArrayList<>();
        private final List<String> passwordPrompts = new ArrayList<>();
        private boolean failOnPrompt;

        @Override
        public String prompt(String message) throws PrompterException {
            if (failOnPrompt) {
                throw new PrompterException("failed");
            }
            prompts.add(message);
            return "answer:" + message;
        }

        @Override
        public String prompt(String message, String defaultReply) throws PrompterException {
            return prompt(message);
        }

        @Override
        public String prompt(String message, List possibleValues) throws PrompterException {
            return prompt(message);
        }

        @Override
        public String prompt(String message, List possibleValues, String defaultReply) throws PrompterException {
            return prompt(message);
        }

        @Override
        public String promptForPassword(String message) throws PrompterException {
            if (failOnPrompt) {
                throw new PrompterException("failed");
            }
            passwordPrompts.add(message);
            return "password:" + message;
        }

        @Override
        public void showMessage(String message) {
        }
    }

    private static final class RecordingUserInfo implements UserInfo {
        private final List<String> messages = new ArrayList<>();

        @Override
        public String getPassphrase() {
            return "delegate-passphrase";
        }

        @Override
        public String getPassword() {
            return "delegate-password";
        }

        @Override
        public boolean promptPassword(String message) {
            return true;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {
            messages.add(message);
        }
    }
}
