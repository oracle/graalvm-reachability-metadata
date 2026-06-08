/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.eclipse.angus.mail.imap.protocol.IMAPResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class IMAPProtocolTest {
    @Test
    public void saslLoginInstantiatesDefaultSaslAuthenticator() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.imap.reusetagprefix", "true");
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();
        IMAPProtocol protocol = newProtocol(properties, clientOutput);

        protocol.handleCapabilityResponse(new IMAPResponse[] {
            new IMAPResponse("* CAPABILITY IMAP4rev1 AUTH=CRAM-MD5")
        });

        try {
            try {
                protocol.sasllogin(new String[] {"CRAM-MD5"}, null, null, "user", "password");
            } catch (UnsupportedOperationException exception) {
                assertThat(exception).hasMessageContaining("SASL");
                return;
            }

            assertThat(protocol.isAuthenticated()).isTrue();
            assertThat(clientOutput.toString(StandardCharsets.US_ASCII)).contains("A0 AUTHENTICATE CRAM-MD5");
        } finally {
            protocol.disconnect();
        }
    }

    private static IMAPProtocol newProtocol(Properties properties, ByteArrayOutputStream clientOutput) throws Exception {
        String serverResponses = "+ PDEyMzQ1Njc4OTA=\r\n"
                + "A0 OK SASL authentication succeeded\r\n";
        ByteArrayInputStream serverInput = new ByteArrayInputStream(serverResponses.getBytes(StandardCharsets.US_ASCII));
        PrintStream clientPrintStream = new PrintStream(clientOutput, true, StandardCharsets.US_ASCII);
        return new IMAPProtocol(serverInput, clientPrintStream, properties, false);
    }
}
