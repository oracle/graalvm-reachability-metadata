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
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JWE payload encryption and decryption for a service protecting customer data.
public class JwePayloadEncryptionTest {
    @Test
    @Timeout(60)
    public void encryptsAndDecryptsCustomerProfile() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String profile = "{\"customerId\":\"customer-42\",\"tier\":\"gold\"}";
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A256GCM)
                .type(JOSEObjectType.JOSE)
                .contentType("application/json")
                .keyID("customer-profile-key")
                .build();
        JWEObject encryptedProfile = new JWEObject(header, new Payload(profile));
        encryptedProfile.encrypt(new RSAEncrypter(publicKey));

        String serializedProfile = encryptedProfile.serialize();
        assertFalse(serializedProfile.contains(profile));

        JWEObject receivedProfile = JWEObject.parse(serializedProfile);
        receivedProfile.decrypt(new RSADecrypter(privateKey));

        assertEquals(JWEObject.State.DECRYPTED, receivedProfile.getState());
        assertEquals(JWEAlgorithm.RSA_OAEP, receivedProfile.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A256GCM, receivedProfile.getHeader().getEncryptionMethod());
        assertEquals("application/json", receivedProfile.getHeader().getContentType());
        assertEquals("customer-profile-key", receivedProfile.getHeader().getKeyID());
        assertEquals(profile, receivedProfile.getPayload().toString());
    }
}
