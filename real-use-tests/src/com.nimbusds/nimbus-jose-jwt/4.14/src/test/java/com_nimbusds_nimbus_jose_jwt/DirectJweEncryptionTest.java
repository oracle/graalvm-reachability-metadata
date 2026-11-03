/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises direct symmetric JWE encryption for a service protecting an internal event.
public class DirectJweEncryptionTest {
    @Test
    @Timeout(60)
    public void encryptsAndDecryptsInternalEventWithSharedKey() throws Exception {
        byte[] encryptionKey = new byte[] {
                0x10, 0x21, 0x32, 0x43, 0x54, 0x65, 0x76, 0x07,
                0x18, 0x29, 0x3a, 0x4b, 0x5c, 0x6d, 0x7e, 0x0f,
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x08,
                0x19, 0x2a, 0x3b, 0x4c, 0x5d, 0x6e, 0x7f, 0x00
        };
        String event = "{\"event\":\"invoice.paid\",\"invoiceId\":\"inv-42\"}";
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                .type(JOSEObjectType.JOSE)
                .contentType("application/json")
                .keyID("internal-event-key")
                .build();
        JWEObject protectedEvent = new JWEObject(header, new Payload(event));
        protectedEvent.encrypt(new DirectEncrypter(encryptionKey));

        String serializedEvent = protectedEvent.serialize();
        assertFalse(serializedEvent.contains(event));

        JWEObject receivedEvent = JWEObject.parse(serializedEvent);
        receivedEvent.decrypt(new DirectDecrypter(Arrays.copyOf(encryptionKey, encryptionKey.length)));

        assertEquals(JWEObject.State.DECRYPTED, receivedEvent.getState());
        assertEquals(JWEAlgorithm.DIR, receivedEvent.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A256GCM, receivedEvent.getHeader().getEncryptionMethod());
        assertEquals("application/json", receivedEvent.getHeader().getContentType());
        assertEquals("internal-event-key", receivedEvent.getHeader().getKeyID());
        assertEquals(event, receivedEvent.getPayload().toString());
    }
}
