/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Decodes quoted-printable text bodies received by an email notification service.
public class QuotedPrintableBodyDecodingTest {
    @Test
    @Timeout(60)
    public void decodesWrappedUtf8NotificationBody() throws Exception {
        String wireBody = "Deployment=20completed=20successfully.=0D=0A"
                + "Release=20candidate=3A=20caf=C3=A9=20service.=0D=0A"
                + "See=20the=20dashboard=20for=20details.=0D=0A";

        try (QuotedPrintableInputStream decodedBody = new QuotedPrintableInputStream(
                new ByteArrayInputStream(wireBody.getBytes(StandardCharsets.US_ASCII)))) {
            assertEquals("Deployment completed successfully.\r\n"
                    + "Release candidate: caf\u00e9 service.\r\n"
                    + "See the dashboard for details.\r\n",
                    new String(decodedBody.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
