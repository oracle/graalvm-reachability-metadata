/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Brotli decompression subsystem by installing a precompressed page in an offline documentation cache.
 */
public class BrotliOfflineDocumentationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void installsPublisherCompressedHtmlForOfflineReading() throws IOException {
        String publishedPage = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><title>Offline installation guide</title></head>
                <body>
                <h1>Install the field console</h1>
                <p>Open the caf\u00e9 dashboard after connecting the sensor.</p>
                <nav><a href="pairing.html">Pair a sensor</a><a href="troubleshooting.html">Troubleshooting</a></nav>
                </body>
                </html>
                """;
        // Publisher fixture: this UTF-8 page compressed with Node.js zlib.brotliCompressSync.
        // An independent encoder supplies the .br file; Commons Compress provides only its decoder.
        String compressedPage = """
                G0oBABwHdmwxTHfyxet0brFhKIzUP4VhASbOEIoJQpVaCk+WO9AA+f4PagvaogdJtP8lRQkmnm7zMejpikwr
                J4gERpONtFujwNkCP4VZMyUuWpIDdR5cgeYC3PyZ4+raESNqeOOSVAX6Z8L+FtpvIeHPlGdvrRLSoVw0Ak+
                83gobQQQjEk8VW7w1icWnPfO2QysY6nnx2wI=
                """;
        Path download = temporaryDirectory.resolve("installation.html.br");
        Files.write(download, Base64.getMimeDecoder().decode(compressedPage));

        Path cache = Files.createDirectory(temporaryDirectory.resolve("offline-help"));
        Path cachedPage = cache.resolve("installation.html");
        try (BufferedInputStream source = new BufferedInputStream(Files.newInputStream(download));
                BrotliCompressorInputStream decoded = new BrotliCompressorInputStream(source)) {
            Files.copy(decoded, cachedPage);
            assertEquals(-1, decoded.read());
        }

        // The offline viewer reads an ordinary UTF-8 HTML file, with no content-encoding support needed.
        String offlinePage = Files.readString(cachedPage, StandardCharsets.UTF_8);
        assertEquals(publishedPage, offlinePage);
        assertEquals(publishedPage.getBytes(StandardCharsets.UTF_8).length, Files.size(cachedPage));
        assertTrue(offlinePage.contains("<title>Offline installation guide</title>"));
        assertTrue(offlinePage.contains("caf\u00e9 dashboard"));
        assertTrue(offlinePage.contains("href=\"pairing.html\""));
        assertTrue(offlinePage.contains("href=\"troubleshooting.html\""));
    }
}
