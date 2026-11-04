/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Encodes international display names and subjects for an outbound email composer.
public class OutboundInternationalHeaderEncodingTest {
    @Test
    @Timeout(60)
    public void encodesAddressAndSubjectHeadersForDelivery() {
        String displayName = EncoderUtil.encodeEncodedWord("J\u00F6hn Doe", EncoderUtil.Usage.WORD_ENTITY,
                50, StandardCharsets.UTF_8, EncoderUtil.Encoding.B);
        String subject = EncoderUtil.encodeEncodedWord("Deployment caf\u00E9 report", EncoderUtil.Usage.WORD_ENTITY,
                50, StandardCharsets.UTF_8, EncoderUtil.Encoding.Q);

        assertEquals("=?UTF-8?B?SsO2aG4gRG9l?=", displayName);
        assertEquals("=?UTF-8?Q?Deployment?= =?UTF-8?Q?_caf=C3=A9_report?=", subject);
        assertEquals("J\u00F6hn Doe", DecoderUtil.decodeEncodedWords(displayName, StandardCharsets.UTF_8));
        assertEquals("Deployment caf\u00E9 report", DecoderUtil.decodeEncodedWords(subject, StandardCharsets.UTF_8));
    }
}
