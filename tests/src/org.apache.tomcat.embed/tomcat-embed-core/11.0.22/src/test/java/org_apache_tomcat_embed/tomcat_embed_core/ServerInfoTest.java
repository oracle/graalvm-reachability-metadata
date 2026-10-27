/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerInfoTest {

    @Test
    void printsRuntimeAndNativeLibraryInformation(@TempDir Path temporaryDirectory) throws Exception {
        String originalLibraryPath = System.getProperty("java.library.path");
        makeVersionedOpenSslDiscoverable(temporaryDirectory, originalLibraryPath);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ServerInfo.main(new String[0]);
        } finally {
            System.setOut(originalOut);
            System.setProperty("java.library.path", originalLibraryPath);
        }

        String report = output.toString(StandardCharsets.UTF_8);
        assertThat(report).contains("Server version:", "OS Name:", "JVM Vendor:", "APR loaded:");
        assertThat(report.contains("OpenSSL (FFM):")).isEqualTo(OpenSSLStatus.isAvailable());
    }

    private static void makeVersionedOpenSslDiscoverable(Path temporaryDirectory, String libraryPath)
            throws Exception {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            return;
        }

        Optional<Path> openSslLibrary = findVersionedOpenSslLibrary(libraryPath);
        if (openSslLibrary.isPresent()) {
            Files.createSymbolicLink(temporaryDirectory.resolve(System.mapLibraryName("ssl")),
                    openSslLibrary.get().toAbsolutePath());
            System.setProperty("java.library.path", temporaryDirectory + System.getProperty("path.separator") +
                    libraryPath);
        }
    }

    private static Optional<Path> findVersionedOpenSslLibrary(String libraryPath) throws Exception {
        String[] searchDirectories = libraryPath.split(System.getProperty("path.separator"));
        for (String searchDirectory : searchDirectories) {
            Path directory = Path.of(searchDirectory);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> candidates = Files.find(directory, 2,
                    (path, attributes) -> attributes.isRegularFile() &&
                            path.getFileName().toString().matches("libssl\\.so(?:\\.\\d+)+"),
                    FileVisitOption.FOLLOW_LINKS)) {
                Optional<Path> candidate = candidates.findFirst();
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
        }
        return Optional.empty();
    }
}
