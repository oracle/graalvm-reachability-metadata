/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFiles;
import org.springframework.boot.devtools.restart.server.HttpRestartServer;
import org.springframework.boot.devtools.restart.server.RestartServer;
import org.springframework.boot.devtools.restart.server.SourceDirectoryUrlFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpAsyncRequestControl;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpRestartServerTest {

    @Test
    void handlesSerializedClassLoaderFiles() throws Exception {
        ClassLoaderFiles files = new ClassLoaderFiles();
        files.addFile("classes", "example.txt", new ClassLoaderFile(ClassLoaderFile.Kind.MODIFIED, new byte[] { 1 }));
        byte[] content = serialize(files);
        RecordingRestartServer restartServer = new RecordingRestartServer();
        HttpRestartServer server = new HttpRestartServer(restartServer);
        RecordingServerHttpResponse response = new RecordingServerHttpResponse();

        server.handle(new SerializedFilesRequest(content), response);

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK);
        assertThat(restartServer.files).isNotNull();
        assertThat(restartServer.files.size()).isEqualTo(1);
        assertThat(restartServer.files.getFile("example.txt").getContents()).containsExactly(1);
    }

    private byte[] serialize(ClassLoaderFiles files) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(files);
            return output.toByteArray();
        }
    }

    private static final class RecordingRestartServer extends RestartServer {

        private ClassLoaderFiles files;

        private RecordingRestartServer() {
            super(new SourceDirectoryUrlFilter() {
                @Override
                public boolean isMatch(String sourceDirectory, URL url) {
                    return false;
                }
            });
        }

        @Override
        public void updateAndRestart(ClassLoaderFiles files) {
            this.files = files;
        }

    }

    private static final class SerializedFilesRequest implements ServerHttpRequest {

        private final byte[] content;

        private final HttpHeaders headers;

        private SerializedFilesRequest(byte[] content) {
            this.content = content;
            this.headers = new HttpHeaders();
            this.headers.setContentLength(content.length);
        }

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.POST;
        }

        @Override
        public URI getURI() {
            return URI.create("http://localhost/restart");
        }

        @Override
        public Map<String, Object> getAttributes() {
            return new HashMap<>();
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public ByteArrayInputStream getBody() {
            return new ByteArrayInputStream(this.content);
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response) {
            return null;
        }

    }

    private static final class RecordingServerHttpResponse implements ServerHttpResponse {

        private final HttpHeaders headers = new HttpHeaders();

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private HttpStatusCode statusCode;

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public OutputStream getBody() {
            return this.body;
        }

        @Override
        public void setStatusCode(HttpStatusCode statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

    }

}
