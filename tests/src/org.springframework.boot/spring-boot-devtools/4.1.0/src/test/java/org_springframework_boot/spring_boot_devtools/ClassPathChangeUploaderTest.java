/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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

public class ClassPathChangeUploaderTest {

    @TempDir
    Path tempDir;

    @Test
    void onApplicationEventSerializesClassLoaderFilesBeforeUploading() throws IOException {
        final File sourceDirectory = this.tempDir.toFile();
        final Path changedPath = this.tempDir.resolve("example.txt");
        Files.writeString(changedPath, "updated content", StandardCharsets.UTF_8);
        final ChangedFile changedFile = new ChangedFile(sourceDirectory, changedPath.toFile(), ChangedFile.Type.ADD);
        final ChangedFiles changedFiles = new ChangedFiles(sourceDirectory, Set.of(changedFile));
        final ClassPathChangedEvent event = new ClassPathChangedEvent(this, Set.of(changedFiles), true);
        final RecordingRequestFactory requestFactory = new RecordingRequestFactory();
        final ClassPathChangeUploader uploader = new ClassPathChangeUploader("https://example.com/restart", requestFactory);

        uploader.onApplicationEvent(event);

        final RecordingRequest request = requestFactory.lastRequest;
        assertNotNull(request);
        assertEquals(URI.create("https://example.com/restart"), request.uri);
        assertEquals(HttpMethod.POST, request.method);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, request.headers.getContentType());
        assertEquals(request.body.size(), request.headers.getContentLength());
        assertTrue(request.body.size() > 0);
        assertTrue(request.executed);
    }

    private static final class RecordingRequestFactory implements ClientHttpRequestFactory {

        private RecordingRequest lastRequest;

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            this.lastRequest = new RecordingRequest(uri, httpMethod);
            return this.lastRequest;
        }

    }

    private static final class RecordingRequest implements ClientHttpRequest {

        private final URI uri;

        private final HttpMethod method;

        private final HttpHeaders headers = new HttpHeaders();

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private boolean executed;

        private RecordingRequest(URI uri, HttpMethod method) {
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

        @Override
        public Map<String, Object> getAttributes() {
            return Collections.emptyMap();
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
            return new OkResponse();
        }

    }

    private static final class OkResponse implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public String getStatusText() {
            return "OK";
        }

        @Override
        public HttpHeaders getHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void close() {
            // No response resources to release.
        }

    }

}
