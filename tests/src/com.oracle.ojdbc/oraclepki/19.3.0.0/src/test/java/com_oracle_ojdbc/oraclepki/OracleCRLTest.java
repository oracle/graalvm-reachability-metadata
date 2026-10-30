/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import oracle.security.pki.OracleCRL;
import org.junit.jupiter.api.Test;

public class OracleCRLTest {
    @Test
    void rejectsMalformedCrlBeforeConnectingToDirectory() throws Exception {
        byte[] malformed = "-----BEGIN X509 CRL-----\nAAAA\n-----END X509 CRL-----\n"
                .getBytes(StandardCharsets.US_ASCII);
        try (ByteArrayInputStream input = new ByteArrayInputStream(malformed)) {
            IOException error = assertThrows(IOException.class,
                    () -> OracleCRL.uploadCRL(null, null, false, input,
                            "localhost", "636", "cn=admin", "password".toCharArray(), false));
            assertThat(error.getMessage()).isNotBlank();
        }
    }
}
