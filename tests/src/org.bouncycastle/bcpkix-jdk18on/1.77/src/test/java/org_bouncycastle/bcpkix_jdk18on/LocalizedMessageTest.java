/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.TimeZone;
import org.bouncycastle.pkix.util.LocalizedMessage;
import org.junit.jupiter.api.Test;

public class LocalizedMessageTest {

    private static final String RESOURCE = "org.bouncycastle.pkix.CertPathReviewerMessages";
    private static final String MESSAGE_ID = "CertPathReviewer.emptyCertPath";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    void resolvesMessageWithDefaultClassLoader() throws Exception {
        LocalizedMessage message = new LocalizedMessage(RESOURCE, MESSAGE_ID);

        String result = message.getEntry("text", Locale.ENGLISH, UTC);

        assertThat(result).isEqualTo("PKIXCertPathReviewer: the CertPath is empty.");
    }

    @Test
    void resolvesMessageWithExplicitClassLoader() throws Exception {
        LocalizedMessage message = new LocalizedMessage(RESOURCE, MESSAGE_ID);
        message.setClassLoader(LocalizedMessage.class.getClassLoader());

        String result = message.getEntry("text", Locale.GERMAN, UTC);

        assertThat(result)
                .isEqualTo("PKIXCertPathReviewer: der Zertifizierungspfad ist leer.");
    }
}
