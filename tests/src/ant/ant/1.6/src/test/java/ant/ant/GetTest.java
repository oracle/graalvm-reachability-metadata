/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Get;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class GetTest {
    private static final String USERNAME = "ant-user";
    private static final String PASSWORD = "ant-password";
    private static final String CONTENT = "downloaded content";

    @TempDir
    Path tempDirectory;

    @Test
    void downloadsUrlWithBasicAuthentication() throws Exception {
        CapturingUrlConnection connection = new CapturingUrlConnection(CONTENT);
        URL source = new URL(null, "memory://download/source.txt", new SingleConnectionHandler(connection));
        Path destination = tempDirectory.resolve("downloaded.txt");

        Get get = newGetTask();
        get.setSrc(source);
        get.setDest(destination.toFile());
        get.setUsername(USERNAME);
        get.setPassword(PASSWORD);

        get.execute();

        String encodedCredentials = Base64.getEncoder().encodeToString(
                (USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.getRequestProperty("Authorization"))
                .isIn("Basic legacy-" + encodedCredentials, "Basic " + encodedCredentials);
        assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
    }

    private Get newGetTask() {
        Project project = new Project();
        project.init();
        Get get = new Get();
        get.setProject(project);
        get.setTaskName("get");
        return get;
    }

    private static final class SingleConnectionHandler extends URLStreamHandler {
        private final URLConnection connection;

        SingleConnectionHandler(URLConnection connection) {
            this.connection = connection;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return connection;
        }
    }

    private static final class CapturingUrlConnection extends URLConnection {
        private final byte[] content;
        private boolean connected;

        CapturingUrlConnection(String content) {
            super(null);
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        boolean isConnected() {
            return connected;
        }
    }
}
