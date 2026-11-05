/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.io.MaxHeaderLengthLimitException;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Applies bounded MIME parser settings to protect an inbound mail endpoint from oversized headers.
public class InboundMimeResourceLimitsTest {
    @Test
    @Timeout(60)
    public void rejectsHeaderThatExceedsTheConfiguredGatewayLimit() throws Exception {
        MimeConfig config = MimeConfig.custom()
                .setMaxHeaderLen(32)
                .setMaxLineLen(128)
                .build();
        MimeTokenStream tokens = new MimeTokenStream(config);
        String message = "Subject: this subject is intentionally too long\r\n"
                + "\r\n"
                + "message body\r\n";

        tokens.parse(new ByteArrayInputStream(message.getBytes(StandardCharsets.US_ASCII)));
        MaxHeaderLengthLimitException failure = assertThrows(MaxHeaderLengthLimitException.class,
                () -> {
                    while (tokens.next() != EntityState.T_END_OF_STREAM) {
                        // Consume the stream as the gateway would before dispatching the message.
                    }
                });

        assertEquals("Maximum header length limit (32) exceeded", failure.getMessage());
    }
}
