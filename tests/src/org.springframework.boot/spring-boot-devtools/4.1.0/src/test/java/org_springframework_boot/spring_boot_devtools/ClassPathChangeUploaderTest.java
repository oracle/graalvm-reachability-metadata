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
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.devtools.classpath.ClassPathChangedEvent;
import org.springframework.boot.devtools.filewatch.ChangedFile;
import org.springframework.boot.devtools.filewatch.ChangedFiles;
import org.springframework.boot.devtools.remote.client.ClassPathChangeUploader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathChangeUploaderTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadsSerializedClassLoaderFilesForClasspathChange() throws Exception {
        Path changedFile = this.tempDir.resolve("com/example/RemoteApplication.class");
        Files.createDirectories(changedFile.getParent());
        Files.writeString(changedFile, "compiled bytecode", StandardCharsets.UTF_8);
        ChangedFile changedClass = new ChangedFile(this.tempDir.toFile(), changedFile.toFile(), ChangedFile.Type.ADD);
        ChangedFiles changedFiles = new ChangedFiles(this.tempDir.toFile(), Set.of(changedClass));
        ClassPathChangedEvent event = new ClassPathChangedEvent(this, Set.of(changedFiles), false);
        CapturingClientHttpRequestFactory requestFactory = new CapturingClientHttpRequestFactory();

        ClassPathChangeUploader uploader = new ClassPathChangeUploader("https://example.com/restart", requestFactory);
        uploader.onApplicationEvent(event);

        CapturingClientHttpRequest request = requestFactory.lastRequest;
        assertThat(request).isNotNull();
        assertThat(request.getURI()).isEqualTo(URI.create("https://example.com/restart"));
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(request.getHeaders().getContentLength()).isEqualTo(request.body.size());
        assertThat(request.body.toByteArray()).startsWith((byte) 0xAC, (byte) 0xED, (byte) 0x00, (byte) 0x05);
        assertThat(request.executed).isTrue();
    }

    private static final class CapturingClientHttpRequestFactory implements ClientHttpRequestFactory {

        private CapturingClientHttpRequest lastRequest;

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            this.lastRequest = new CapturingClientHttpRequest(uri, httpMethod);
            return this.lastRequest;
        }

    }

    private static final class CapturingClientHttpRequest implements ClientHttpRequest {

        private final URI uri;

        private final HttpMethod method;

        private final HttpHeaders headers = new HttpHeaders();

        private final Map<String, Object> attributes = new HashMap<>();

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private boolean executed;

        private CapturingClientHttpRequest(URI uri, HttpMethod method) {
            this.uri = uri;
            this.method = method;
        }

        @Override
        public HttpMethod getMethod() {
            return this.method;
        }

        @Override
        public URI getURI() {
            return this.uri;
        }

        public Map<String, Object> getAttributes() {
            return this.attributes;
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
        public ClientHttpResponse execute() {
            this.executed = true;
            return new OkClientHttpResponse();
        }

    }

    private static final class OkClientHttpResponse implements ClientHttpResponse {

        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public HttpStatusCode getStatusCode() {
            return HttpStatus.OK;
        }

        public int getRawStatusCode() {
            return HttpStatus.OK.value();
        }

        @Override
        public String getStatusText() {
            return HttpStatus.OK.getReasonPhrase();
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void close() {
        }

    }

}
