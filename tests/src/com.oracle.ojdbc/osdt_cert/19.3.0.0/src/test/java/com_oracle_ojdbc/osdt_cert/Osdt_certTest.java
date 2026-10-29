/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.osdt_cert;

import oracle.security.crypto.cert.X509Extension;
import oracle.security.crypto.cert.ext.BasicConstraintsExtension;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Osdt_certTest {
    @Test
    void decodesEndEntityBasicConstraints() throws IOException {
        byte[] encoded = {0x30, 0x09, 0x06, 0x03, 0x55, 0x1d, 0x13, 0x04, 0x02, 0x30, 0x00};

        BasicConstraintsExtension decoded = assertInstanceOf(BasicConstraintsExtension.class,
                X509Extension.inputInstance(new ByteArrayInputStream(encoded)));

        assertTrue(decoded.isRecognized());
        assertFalse(decoded.getCritical());
        assertFalse(decoded.getCA());
        assertNull(decoded.getPathLen());
    }
}
