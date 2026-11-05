/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Pulls a customer notification through Mime4j's token stream for header and body processing.
public class InboundTokenStreamMessageTest {
    @Test
    @Timeout(60)
    public void readsHeadersAndDecodedBodyAsApplicationConsumesTokens() throws Exception {
        String message = "From: notifications@example.test\r\n"
                + "Subject: build report\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "Build=20succeeded=20for=20release-42.=0A"
                + "Artifacts=3A=20app.jar\r\n";
        MimeTokenStream tokens = new MimeTokenStream(MimeConfig.DEFAULT);
        tokens.parse(new ByteArrayInputStream(message.getBytes(StandardCharsets.US_ASCII)));
        Notification notification = consume(tokens);

        assertEquals(Map.of("from", "notifications@example.test", "subject", "build report",
                        "content-type", "text/plain; charset=UTF-8",
                        "content-transfer-encoding", "quoted-printable"), notification.headers());
        assertEquals("text/plain", notification.bodyDescriptor().getMimeType());
        assertEquals("UTF-8", notification.bodyDescriptor().getCharset());
        assertEquals("Build succeeded for release-42.\nArtifacts: app.jar\r\n",
                notification.body());
    }

    private static Notification consume(MimeTokenStream tokens) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        BodyDescriptor bodyDescriptor = null;
        String body = null;
        EntityState state;
        while ((state = tokens.next()) != EntityState.T_END_OF_STREAM) {
            if (state == EntityState.T_FIELD) {
                Field field = tokens.getField();
                headers.put(field.getName().toLowerCase(Locale.ROOT), field.getBody());
            } else if (state == EntityState.T_BODY) {
                bodyDescriptor = tokens.getBodyDescriptor();
                body = new String(tokens.getDecodedInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new Notification(headers, bodyDescriptor, body);
    }

    private record Notification(Map<String, String> headers, BodyDescriptor bodyDescriptor, String body) {
    }
}
