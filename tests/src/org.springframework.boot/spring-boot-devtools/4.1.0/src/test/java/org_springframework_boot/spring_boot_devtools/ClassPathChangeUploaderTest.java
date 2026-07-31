/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.devtools.classpath.ClassPathChangedEvent;
import org.springframework.boot.devtools.filewatch.ChangedFile;
import org.springframework.boot.devtools.filewatch.ChangedFiles;
import org.springframework.boot.devtools.remote.client.ClassPathChangeUploader;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathChangeUploaderTest {

    @Test
    void uploadsSerializedClassPathChanges(@TempDir Path temporaryDirectory) throws Exception {
        AtomicReference<byte[]> uploadedContent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/remote", (exchange) -> {
            try (InputStream requestBody = exchange.getRequestBody()) {
                uploadedContent.set(requestBody.readAllBytes());
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            Path changedFile = temporaryDirectory.resolve("example.txt");
            Files.writeString(changedFile, "changed classpath content", StandardCharsets.UTF_8);
            ChangedFiles changedFiles = new ChangedFiles(temporaryDirectory.toFile(),
                    Set.of(new ChangedFile(temporaryDirectory.toFile(), changedFile.toFile(), ChangedFile.Type.ADD)));
            ClassPathChangedEvent event = new ClassPathChangedEvent(this, Set.of(changedFiles), false);
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(10));
            ClassPathChangeUploader uploader = new ClassPathChangeUploader(
                    "http://localhost:" + server.getAddress().getPort() + "/remote", requestFactory);

            uploader.onApplicationEvent(event);

            assertThat(uploadedContent.get()).isNotNull().isNotEmpty();
        }
        finally {
            server.stop(0);
        }
    }
}
