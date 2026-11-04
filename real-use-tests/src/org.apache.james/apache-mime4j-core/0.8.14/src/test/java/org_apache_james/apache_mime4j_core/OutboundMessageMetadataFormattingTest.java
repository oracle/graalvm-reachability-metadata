/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.util.MimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Formats outbound message dates and folded headers for an email delivery service.
public class OutboundMessageMetadataFormattingTest {
    @Test
    @Timeout(60)
    public void formatsDeliveryMetadataAndClassifiesMimeParts() {
        String subject = "Subject: Deployment completed successfully for customer account 42";

        assertEquals("Subject: Deployment completed\r\n"
                        + " successfully for customer account 42",
                MimeUtil.fold(subject, 36));
        assertEquals(subject, MimeUtil.unfold(MimeUtil.fold(subject, 36)));
        assertEquals("Thu, 1 Jan 1970 00:00:00 +0000",
                MimeUtil.formatDate(new Date(0), TimeZone.getTimeZone("GMT")));
        assertTrue(MimeUtil.isMultipart("multipart/mixed"));
        assertTrue(MimeUtil.isMessage("message/rfc822"));
        assertTrue(MimeUtil.isSameMimeType("TEXT/PLAIN", "text/plain"));
    }
}
