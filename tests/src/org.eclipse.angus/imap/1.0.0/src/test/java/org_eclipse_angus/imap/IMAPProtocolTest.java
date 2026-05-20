/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.sun.mail.iap.Response;
import com.sun.mail.imap.protocol.IMAPProtocol;
import com.sun.mail.imap.protocol.IMAPResponse;
import org.junit.jupiter.api.Test;

public class IMAPProtocolTest {
    private static final String UNSUPPORTED_SASL_MECHANISM = "FORGE";

    @Test
    void saslAuthenticatorIsInstantiatedWhenServerAdvertisesAllowedSaslMechanism()
            throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IMAPProtocol protocol = new IMAPProtocol(input, new PrintStream(output, true,
            StandardCharsets.US_ASCII), new Properties(), false);
        protocol.handleCapabilityResponse(new Response[] {
            new IMAPResponse("* CAPABILITY IMAP4rev1 AUTH=" + UNSUPPORTED_SASL_MECHANISM)
        });

        assertThatThrownBy(() -> protocol.sasllogin(new String[] {UNSUPPORTED_SASL_MECHANISM},
            null, null, "user", "password"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("No SASL support");
    }
}
