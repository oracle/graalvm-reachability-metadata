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
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile.Kind;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFiles;
import org.springframework.boot.devtools.restart.server.HttpRestartServer;
import org.springframework.boot.devtools.restart.server.RestartServer;
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
    void handleReadsSerializedClassLoaderFilesAndUpdatesRestartServer() throws Exception {
        byte[] contents = "updated contents".getBytes(StandardCharsets.UTF_8);
        ClassLoaderFiles files = new ClassLoaderFiles();
        files.addFile("src/main/classes", "com/example/Example.class", new ClassLoaderFile(Kind.MODIFIED, contents));
        CapturingRestartServer restartServer = new CapturingRestartServer();
        HttpRestartServer server = new HttpRestartServer(restartServer);
        byte[] body = serialize(files);
        ByteArrayServerHttpRequest request = new ByteArrayServerHttpRequest(body);
        CapturingServerHttpResponse response = new CapturingServerHttpResponse();

        server.handle(request, response);

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK);
        assertThat(restartServer.files).isNotNull();
        ClassLoaderFile uploadedFile = restartServer.files.getFile("com/example/Example.class");
        assertThat(uploadedFile).isNotNull();
        assertThat(uploadedFile.getKind()).isEqualTo(Kind.MODIFIED);
        assertThat(uploadedFile.getContents()).containsExactly(contents);
    }

    private static byte[] serialize(ClassLoaderFiles files) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(files);
        }
        return output.toByteArray();
    }

    private static final class CapturingRestartServer extends RestartServer {

        private ClassLoaderFiles files;

        private CapturingRestartServer() {
            super((sourceDirectory, url) -> false);
        }

        @Override
        public void updateAndRestart(ClassLoaderFiles files) {
            this.files = files;
        }

    }

    private static final class ByteArrayServerHttpRequest implements ServerHttpRequest {

        private final HttpHeaders headers = new HttpHeaders();

        private final Map<String, Object> attributes = new HashMap<>();

        private final byte[] body;

        private ByteArrayServerHttpRequest(byte[] body) {
            this.body = body.clone();
            this.headers.setContentLength(body.length);
        }

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.POST;
        }

        @Override
        public URI getURI() {
            return URI.create("https://example.com/restart");
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return this.attributes;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(this.body);
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
            throw new UnsupportedOperationException("Async request control is not used by this test");
        }

    }

    private static final class CapturingServerHttpResponse implements ServerHttpResponse {

        private final HttpHeaders headers = new HttpHeaders();

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private HttpStatusCode statusCode;

        @Override
        public void setStatusCode(HttpStatusCode statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public OutputStream getBody() {
            return this.body;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

    }

}
