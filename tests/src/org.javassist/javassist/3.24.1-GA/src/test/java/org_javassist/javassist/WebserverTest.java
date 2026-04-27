/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import javassist.tools.web.Webserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WebserverTest {
    private static final byte[] CLASS_FILE_MAGIC = new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe};

    @TempDir
    Path temporaryDirectory;

    @Test
    void classRequestFallsBackToClasspathResourceWhenFileIsMissing() throws Exception {
        Webserver server = new Webserver(0);
        server.htmlfileBase = temporaryDirectory.toString() + File.separator;

        try {
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            server.doReply(
                    new ByteArrayInputStream(new byte[0]),
                    response,
                    "GET /javassist/tools/web/Webserver.class HTTP/1.0");

            byte[] responseBytes = response.toByteArray();
            int bodyStart = indexAfterHeader(responseBytes);

            assertThat(new String(responseBytes, 0, bodyStart, StandardCharsets.US_ASCII))
                    .contains("HTTP/1.0 200 OK")
                    .contains("Content-Type: application/octet-stream");
            assertThat(Arrays.copyOfRange(responseBytes, bodyStart, bodyStart + CLASS_FILE_MAGIC.length))
                    .isEqualTo(CLASS_FILE_MAGIC);
        } finally {
            server.end();
        }
    }

    private static int indexAfterHeader(byte[] responseBytes) {
        for (int i = 0; i < responseBytes.length - 3; i++) {
            if (responseBytes[i] == '\r'
                    && responseBytes[i + 1] == '\n'
                    && responseBytes[i + 2] == '\r'
                    && responseBytes[i + 3] == '\n') {
                return i + 4;
            }
        }
        throw new IllegalStateException("HTTP header terminator not found");
    }
}
