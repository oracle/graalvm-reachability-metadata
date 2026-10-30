/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.auth.NTLMScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class NTLMSchemeTest {
    @Test
    void startsNtlmHandshakeWithTypeOneMessage() throws Exception {
        NTLMScheme scheme = new NTLMScheme();
        scheme.processChallenge("NTLM");
        String header = scheme.authenticate(new NTCredentials("user", "pass", "WORKSTATION", "DOMAIN"), new GetMethod("/private"));
        assertTrue(header.startsWith("NTLM "));
        byte[] message = Base64.getDecoder().decode(header.substring(5));
        assertEquals("NTLMSSP", new String(message, 0, 7, StandardCharsets.US_ASCII));
        assertEquals(1, message[8]);
        assertTrue(scheme.isConnectionBased());
        assertFalse(scheme.isComplete());
    }
}
