/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

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

public class HttpRestartServerTest {

    @Test
    void handleDeserializesClassLoaderFilesAndDelegatesToRestartServer() throws IOException {
        final ClassLoaderFiles files = new ClassLoaderFiles();
        final byte[] requestBody = serialize(files);
        final RecordingRestartServer restartServer = new RecordingRestartServer();
        final HttpRestartServer httpRestartServer = new HttpRestartServer(restartServer);
        final RecordingServerHttpResponse response = new RecordingServerHttpResponse();

        httpRestartServer.handle(new BodyServerHttpRequest(requestBody), response);

        assertEquals(HttpStatus.OK, response.statusCode);
        assertNotNull(restartServer.receivedFiles);
        assertEquals(0, restartServer.receivedFiles.size());
    }

    private static byte[] serialize(ClassLoaderFiles files) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(files);
        }
        return output.toByteArray();
    }

    private static final class RecordingRestartServer extends RestartServer {

        private ClassLoaderFiles receivedFiles;

        private RecordingRestartServer() {
            super((sourceDirectory, url) -> false);
        }

        @Override
        public void updateAndRestart(ClassLoaderFiles files) {
            this.receivedFiles = files;
        }

    }

    private static final class BodyServerHttpRequest implements ServerHttpRequest {

        private final byte[] body;

        private final HttpHeaders headers = new HttpHeaders();

        private BodyServerHttpRequest(byte[] body) {
            this.body = body;
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
        public Map<String, Object> getAttributes() {
            return Collections.emptyMap();
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
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
            return InetSocketAddress.createUnresolved("localhost", 8080);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return InetSocketAddress.createUnresolved("localhost", 0);
        }

        @Override
        public ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response) {
            throw new UnsupportedOperationException("Async request handling is not used by this test");
        }

    }

    private static final class RecordingServerHttpResponse implements ServerHttpResponse {

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
            // No buffered response resources to flush.
        }

        @Override
        public void close() {
            // No response resources to close.
        }

    }

}
