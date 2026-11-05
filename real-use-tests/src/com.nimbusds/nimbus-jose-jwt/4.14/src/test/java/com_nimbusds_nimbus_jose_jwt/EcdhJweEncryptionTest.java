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
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.ECKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises elliptic-curve key agreement for a service encrypting customer data for a recipient.
public class EcdhJweEncryptionTest {
    @Test
    @Timeout(60)
    public void encryptsAndDecryptsCustomerProfileForEcRecipient() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair recipientPair = keyPairGenerator.generateKeyPair();
        ECPublicKey recipientPublicKey = (ECPublicKey) recipientPair.getPublic();
        ECPrivateKey recipientPrivateKey = (ECPrivateKey) recipientPair.getPrivate();

        ECKey recipientKey = new ECKey.Builder(ECKey.Curve.P_256, recipientPublicKey)
                .privateKey(recipientPrivateKey)
                .keyID("customer-profile-recipient")
                .build();
        String profile = "{\"customerId\":\"customer-42\",\"tier\":\"gold\"}";
        JWEHeader header = new JWEHeader.Builder(
                JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM)
                .type(JOSEObjectType.JOSE)
                .contentType("application/json")
                .keyID(recipientKey.getKeyID())
                .build();
        JWEObject encryptedProfile = new JWEObject(header, new Payload(profile));
        encryptedProfile.encrypt(new ECDHEncrypter(recipientKey.toECPublicKey()));

        String serializedProfile = encryptedProfile.serialize();
        assertFalse(serializedProfile.contains(profile));

        JWEObject receivedProfile = JWEObject.parse(serializedProfile);
        receivedProfile.decrypt(new ECDHDecrypter(recipientKey.toECPrivateKey()));

        assertEquals(JWEObject.State.DECRYPTED, receivedProfile.getState());
        assertEquals(JWEAlgorithm.ECDH_ES_A256KW, receivedProfile.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A256GCM, receivedProfile.getHeader().getEncryptionMethod());
        assertEquals("application/json", receivedProfile.getHeader().getContentType());
        assertEquals("customer-profile-recipient", receivedProfile.getHeader().getKeyID());
        assertEquals(profile, receivedProfile.getPayload().toString());
    }
}
