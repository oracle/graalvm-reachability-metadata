/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Get;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GetTest {
    @Test
    void downloadsFileWithBasicAuthenticationHeader(@TempDir Path temporaryDirectory) throws IOException {
        String content = "downloaded by get task";
        RecordingUrlStreamHandler streamHandler = new RecordingUrlStreamHandler(content);
        Path destination = temporaryDirectory.resolve("destination.txt");

        Get get = newGetTask();
        get.setSrc(new URL(null, "memory://get/source.txt", streamHandler));
        get.setDest(destination.toFile());
        get.setUsername("ant-user");
        get.setPassword("ant-password");

        get.execute();

        assertThat(streamHandler.connection.authorizationHeader).isEqualTo("Basic generated-encoder");
        assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    private static Get newGetTask() {
        Get get = new Get();
        get.setProject(new Project());
        get.setTaskName("get");
        return get;
    }

    private static final class RecordingUrlStreamHandler extends URLStreamHandler {
        private final String content;
        private RecordingURLConnection connection;

        private RecordingUrlStreamHandler(String content) {
            this.content = content;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            connection = new RecordingURLConnection(url, content);
            return connection;
        }
    }

    private static final class RecordingURLConnection extends URLConnection {
        private final byte[] content;
        private String authorizationHeader;

        private RecordingURLConnection(URL url, String content) {
            super(url);
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            if ("Authorization".equals(key)) {
                authorizationHeader = value;
            }
            super.setRequestProperty(key, value);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }
    }
}
