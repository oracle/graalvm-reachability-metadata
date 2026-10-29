/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.osdt_cert;

import oracle.security.crypto.cert.X509Extension;
import oracle.security.crypto.cert.ext.BasicConstraintsExtension;
import oracle.security.crypto.cert.ext.KeyUsageExtension;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class X509ExtensionTest {
    @Test
    void decodesCriticalCertificateAuthorityConstraints() throws IOException {
        BasicConstraintsExtension original = new BasicConstraintsExtension(BigInteger.valueOf(3), true);

        BasicConstraintsExtension decoded = assertInstanceOf(BasicConstraintsExtension.class, roundTrip(original));

        assertTrue(decoded.isRecognized());
        assertTrue(decoded.getCritical());
        assertTrue(decoded.getCA());
        assertEquals(BigInteger.valueOf(3), decoded.getPathLen());
        assertEquals(original.getType(), decoded.getType());
        assertArrayEquals(original.getValue(), decoded.getValue());
    }

    @Test
    void decodesNonCriticalKeyUsageFlags() throws IOException {
        KeyUsageExtension original = new KeyUsageExtension(
                new int[]{KeyUsageExtension.DIGITAL_SIGNATURE, KeyUsageExtension.KEY_ENCIPHERMENT}, false);

        KeyUsageExtension decoded = assertInstanceOf(KeyUsageExtension.class, roundTrip(original));

        assertTrue(decoded.isRecognized());
        assertFalse(decoded.getCritical());
        assertTrue(decoded.hasUsageFlag(KeyUsageExtension.DIGITAL_SIGNATURE));
        assertTrue(decoded.hasUsageFlag(KeyUsageExtension.KEY_ENCIPHERMENT));
        assertFalse(decoded.hasUsageFlag(KeyUsageExtension.KEY_CERT_SIGN));
        assertArrayEquals(original.getKeyUsage(), decoded.getKeyUsage());
        assertEquals(original.getType(), decoded.getType());
        assertArrayEquals(original.getValue(), decoded.getValue());
    }

    private static X509Extension roundTrip(X509Extension extension) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        extension.output(encoded);
        return X509Extension.inputInstance(new ByteArrayInputStream(encoded.toByteArray()));
    }
}
