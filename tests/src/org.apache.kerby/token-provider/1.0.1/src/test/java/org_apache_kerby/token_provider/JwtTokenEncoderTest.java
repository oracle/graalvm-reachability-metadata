/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.token_provider;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import org.apache.kerby.kerberos.kerb.type.base.AuthToken;
import org.apache.kerby.kerberos.provider.token.JwtAuthToken;
import org.apache.kerby.kerberos.provider.token.JwtTokenDecoder;
import org.apache.kerby.kerberos.provider.token.JwtTokenEncoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtTokenEncoderTest {
    @Test
    void plainEncodingPreservesClaimsAndAssignsAStableTokenId() throws Exception {
        JwtAuthToken token = token();
        JwtTokenEncoder encoder = new JwtTokenEncoder();
        JwtTokenDecoder decoder = new JwtTokenDecoder();

        String encoded = encoder.encodeAsString(token);
        AuthToken decoded = decoder.decodeFromString(encoded);
        assertClaims(decoded);
        Object id = decoded.getAttributes().get("jti");
        assertNotNull(id);
        assertFalse(id.toString().isEmpty());
        assertEquals(id, token.getAttributes().get("jti"));
        assertEquals(encoded, new String(encoder.encodeAsBytes(token), StandardCharsets.UTF_8));
        assertEquals(id, decoder.decodeFromString(encoder.encodeAsString(token)).getAttributes().get("jti"));
    }

    @Test
    void signsWithSymmetricKeyAndCopiesKeyMaterial() throws Exception {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        JwtTokenEncoder encoder = new JwtTokenEncoder();
        encoder.setJwsAlgorithm(JWSAlgorithm.HS256);
        encoder.setSignKey(key);
        byte[] verificationKey = key.clone();
        JwtTokenDecoder decoder = new JwtTokenDecoder();
        decoder.setVerifyKey(verificationKey);
        Arrays.fill(key, (byte) 0);

        String signed = encoder.encodeAsString(token());
        Arrays.fill(verificationKey, (byte) 0);
        AuthToken decoded = decoder.decodeFromString(signed);
        assertClaims(decoded);
        assertTrue(decoder.isSigned());
        assertEquals(JWSAlgorithm.HS256, encoder.getJwsAlgorithm());
    }

    @Test
    void encryptsClaimsWithADirectSymmetricKey() throws Exception {
        byte[] key = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        JwtTokenEncoder encoder = new JwtTokenEncoder();
        encoder.setJweAlgorithm(JWEAlgorithm.DIR);
        encoder.setEncryptionMethod(EncryptionMethod.A128CBC_HS256);
        encoder.setEncryptionKey(key);
        byte[] decryptionKey = key.clone();
        JwtTokenDecoder decoder = new JwtTokenDecoder();
        decoder.setDecryptionKey(decryptionKey);
        Arrays.fill(key, (byte) 0);

        String encrypted = encoder.encodeAsString(token());
        Arrays.fill(decryptionKey, (byte) 0);
        assertEquals(5, encrypted.split("\\.", -1).length);
        assertClaims(decoder.decodeFromString(encrypted));
        assertFalse(decoder.isSigned());
        assertEquals(JWEAlgorithm.DIR, encoder.getJweAlgorithm());
        assertEquals(EncryptionMethod.A128CBC_HS256, encoder.getEncryptionMethod());
    }

    @Test
    void signsWithRsaAndEncryptsTheSignedTokenForTheRecipient() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair signingKeys = generator.generateKeyPair();
        byte[] encryptionKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        JwtTokenEncoder encoder = new JwtTokenEncoder();
        encoder.setSignKey(signingKeys.getPrivate());
        JwtTokenDecoder signedDecoder = new JwtTokenDecoder();
        signedDecoder.setVerifyKey(signingKeys.getPublic());

        assertClaims(signedDecoder.decodeFromString(encoder.encodeAsString(token())));
        assertTrue(signedDecoder.isSigned());

        encoder.setJweAlgorithm(JWEAlgorithm.DIR);
        encoder.setEncryptionKey(encryptionKey);
        encoder.setEncryptionMethod(EncryptionMethod.A128CBC_HS256);
        JwtTokenDecoder encryptedDecoder = new JwtTokenDecoder();
        encryptedDecoder.setVerifyKey(signingKeys.getPublic());
        encryptedDecoder.setDecryptionKey(encryptionKey);
        String encrypted = encoder.encodeAsString(token());

        assertEquals(5, encrypted.split("\\.", -1).length);
        assertClaims(encryptedDecoder.decodeFromString(encrypted));
        assertTrue(encryptedDecoder.isSigned());
    }

    private static JwtAuthToken token() {
        JwtAuthToken token = new JwtAuthToken();
        token.setSubject("alice");
        token.setIssuer("identity-service");
        token.setAudiences(List.of("ticket-service"));
        token.addAttribute("scope", "read:mail");
        return token;
    }

    private static void assertClaims(AuthToken token) {
        assertNotNull(token);
        assertEquals("alice", token.getSubject());
        assertEquals("identity-service", token.getIssuer());
        assertEquals(List.of("ticket-service"), token.getAudiences());
        assertEquals("read:mail", token.getAttributes().get("scope"));
    }
}
