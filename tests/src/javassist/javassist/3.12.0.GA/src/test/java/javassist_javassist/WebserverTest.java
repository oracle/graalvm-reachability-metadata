/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javassist.tools.web.Webserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebserverTest {
    private static final String CLASSPATH_RESOURCE_NAME = "webserverfixture.class";
    private static final String CLASSPATH_RESOURCE_CONTENT = "webserver classpath fixture";

    @TempDir
    Path htmlDirectory;

    @Test
    void servesClassFileFromClasspathWhenItIsMissingFromHtmlDirectory() throws Exception {
        Webserver webserver = new Webserver(0);
        webserver.htmlfileBase = htmlDirectory.toString() + "/";
        ByteArrayOutputStream response = new ByteArrayOutputStream();

        try {
            webserver.doReply(
                    new ByteArrayInputStream(new byte[0]),
                    response,
                    "GET /" + CLASSPATH_RESOURCE_NAME + " HTTP/1.0");
        } finally {
            webserver.end();
        }

        String responseText = response.toString(StandardCharsets.ISO_8859_1);
        assertThat(responseText)
                .contains("HTTP/1.0 200 OK\r\n")
                .contains("Content-Length: " + CLASSPATH_RESOURCE_CONTENT.length() + "\r\n")
                .contains("Content-Type: application/octet-stream\r\n")
                .endsWith(CLASSPATH_RESOURCE_CONTENT);
    }
}
