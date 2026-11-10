/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.util.Locale;
import java.util.TimeZone;

import org.bouncycastle.i18n.LocalizedMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalizedMessageTest {
    @Test
    void readsLibraryMessageWithDefaultAndExplicitLoaders() {
        LocalizedMessage message = new LocalizedMessage("org.bouncycastle.x509.CertPathReviewerMessages",
                "CertPathReviewer.emptyCertPath");
        TimeZone zone = TimeZone.getTimeZone("UTC");
        assertEquals("CertPath is empty", message.getEntry("title", Locale.ROOT, zone));
        message.setClassLoader(LocalizedMessageTest.class.getClassLoader());
        assertEquals("PKIXCertPathReviewer: the CertPath is empty.",
                message.getEntry("text", Locale.ROOT, zone));
    }
}
