/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_http_client.google_http_client;

import com.google.api.client.util.IOUtils;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class IOUtilsTest {

    @TempDir
    private Path tempDir;

    @Test
    public void serializationRoundTripWritesAndReadsObjectStream() throws IOException {
        String value = "google-http-client-io-utils";

        byte[] bytes = IOUtils.serialize(value);
        Serializable deserialized = IOUtils.deserialize(bytes);

        assertThat(deserialized).isEqualTo(value);
    }

    @Test
    public void isSymbolicLinkUsesNioPathReflection() throws IOException {
        Path regularFile = tempDir.resolve("regular-file.txt");
        Files.writeString(regularFile, "content");

        boolean symbolicLink = IOUtils.isSymbolicLink(regularFile.toFile());

        assertThat(symbolicLink).isFalse();
    }
}
