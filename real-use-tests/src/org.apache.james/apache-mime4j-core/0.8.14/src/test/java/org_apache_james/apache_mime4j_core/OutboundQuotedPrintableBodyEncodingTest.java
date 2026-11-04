/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.codec.QuotedPrintableOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Encodes outbound notification bodies as quoted-printable MIME content.
public class OutboundQuotedPrintableBodyEncodingTest {
    @Test
    @Timeout(60)
    public void encodesUtf8TextAndTransportSensitiveBytesForDelivery() throws Exception {
        ByteArrayOutputStream wireBody = new ByteArrayOutputStream();
        String body = "Deployment caf\u00E9 = ready\r\nNext step\t\r\n";

        try (QuotedPrintableOutputStream encoder = new QuotedPrintableOutputStream(wireBody, false)) {
            encoder.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("Deployment caf=C3=A9 =3D ready\r\nNext step=09\r\n",
                wireBody.toString(StandardCharsets.US_ASCII));
    }
}
