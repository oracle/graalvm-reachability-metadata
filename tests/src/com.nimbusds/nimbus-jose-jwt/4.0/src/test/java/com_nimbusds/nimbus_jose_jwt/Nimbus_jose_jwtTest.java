/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds.nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.CompressionAlgorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.Header;
import com.nimbusds.jose.JOSEObject;
import com.nimbusds.jose.JOSEObjectHandler;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.Payload.Origin;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.PlainObject;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTHandler;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.ReadOnlyJWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Test;

public class Nimbus_jose_jwtTest {
    private static final byte[] HMAC_SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DIRECT_ENCRYPTION_KEY = "abcdef0123456789".getBytes(StandardCharsets.UTF_8);
    private static final Date ISSUED_AT = new Date(1_700_000_000_000L);
    private static final Date NOT_BEFORE = new Date(1_700_000_060_000L);
    private static final Date EXPIRES_AT = new Date(1_700_003_600_000L);

    @Test
    void plainJwtPreservesRegisteredAndTypedCustomClaims() throws Exception {
        JWTClaimsSet claimsSet = claimsSet();
        PlainHeader header = new PlainHeader.Builder()
                .type(JOSEObjectType.JWT)
                .contentType("claims+json")
                .customParam("tenant", "native-image")
                .build();

        PlainJWT jwt = new PlainJWT(header, claimsSet);
        String serialized = jwt.serialize();

        PlainJWT parsed = PlainJWT.parse(serialized);
        ReadOnlyJWTClaimsSet parsedClaims = parsed.getJWTClaimsSet();
        assertEquals("issuer-1", parsedClaims.getIssuer());
        assertEquals("subject-1", parsedClaims.getSubject());
        assertEquals(Arrays.asList("audience-a", "audience-b"), parsedClaims.getAudience());
        assertEquals(EXPIRES_AT, parsedClaims.getExpirationTime());
        assertEquals(NOT_BEFORE, parsedClaims.getNotBeforeTime());
        assertEquals(ISSUED_AT, parsedClaims.getIssueTime());
        assertEquals("jwt-id-1", parsedClaims.getJWTID());
        assertEquals("admin", parsedClaims.getStringClaim("role"));
        assertEquals(Arrays.asList("read", "write"), parsedClaims.getStringListClaim("scopes"));
        assertArrayEquals(new String[] {"read", "write"}, parsedClaims.getStringArrayClaim("scopes"));
        assertEquals(Boolean.TRUE, parsedClaims.getBooleanClaim("active"));
        assertEquals(Integer.valueOf(7), parsedClaims.getIntegerClaim("login_count"));
        assertEquals(Long.valueOf(42L), parsedClaims.getLongClaim("quota"));
        assertEquals(Float.valueOf(1.5f), parsedClaims.getFloatClaim("score"));
        assertEquals(Double.valueOf(2.25d), parsedClaims.getDoubleClaim("ratio"));

        assertEquals("claims+json", parsed.getHeader().getContentType());
        assertEquals("native-image", parsed.getHeader().getCustomParam("tenant"));
        assertEquals("plain", JWTParser.parse(serialized, new JwtKindHandler()));
        assertInstanceOf(PlainJWT.class, JWTParser.parse(serialized));
    }

    @Test
    void signedJwtRoundTripsWithHmacHeaderAndVerificationPolicy() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .contentType("JWT")
                .keyID("hmac-key-1")
                .customParam("environment", "test")
                .build();
        SignedJWT jwt = new SignedJWT(header, claimsSet());

        assertEquals(JWSObject.State.UNSIGNED, jwt.getState());
        jwt.sign(new MACSigner(HMAC_SECRET));
        assertEquals(JWSObject.State.SIGNED, jwt.getState());

        String serialized = jwt.serialize();
        SignedJWT parsed = SignedJWT.parse(serialized);
        assertEquals("hmac-key-1", parsed.getHeader().getKeyID());
        assertEquals("test", parsed.getHeader().getCustomParam("environment"));
        assertEquals(JWSAlgorithm.HS256, parsed.getHeader().getAlgorithm());
        assertEquals("subject-1", parsed.getJWTClaimsSet().getSubject());
        assertEquals("signed", JWTParser.parse(serialized, new JwtKindHandler()));

        MACVerifier verifier = new MACVerifier(HMAC_SECRET);
        verifier.setAcceptedAlgorithms(Collections.singleton(JWSAlgorithm.HS256));
        assertTrue(parsed.verify(verifier));
        assertEquals(JWSObject.State.VERIFIED, parsed.getState());

        MACVerifier wrongSecretVerifier = new MACVerifier("abcdef0123456789abcdef0123456789");
        assertFalse(SignedJWT.parse(serialized).verify(wrongSecretVerifier));
    }

    @Test
    void jwsVerificationHonorsCriticalHeaderParameterPolicy() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .criticalParams(Collections.singleton("tenant-policy"))
                .customParam("tenant-policy", "required")
                .keyID("critical-hmac")
                .build();
        JWSObject jwsObject = new JWSObject(header, new Payload("policy protected payload"));
        jwsObject.sign(new MACSigner(HMAC_SECRET));

        String serialized = jwsObject.serialize();
        JWSObject parsed = JWSObject.parse(serialized);
        assertEquals(Collections.singleton("tenant-policy"), parsed.getHeader().getCriticalParams());
        assertEquals("required", parsed.getHeader().getCustomParam("tenant-policy"));

        assertFalse(parsed.verify(new MACVerifier(HMAC_SECRET)));

        MACVerifier policyAwareVerifier = new MACVerifier(HMAC_SECRET);
        policyAwareVerifier.setIgnoredCriticalHeaderParameters(Collections.singleton("tenant-policy"));
        assertTrue(parsed.verify(policyAwareVerifier));
        assertEquals(JWSObject.State.VERIFIED, parsed.getState());
    }

    @Test
    void signedJoseObjectCanBeNestedAsPayload() throws Exception {
        JWSObject inner = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("nested-hmac").build(),
                new Payload("inner message"));
        inner.sign(new MACSigner(HMAC_SECRET));

        Payload nestedPayload = new Payload(inner);
        assertEquals(Origin.JWS_OBJECT, nestedPayload.getOrigin());
        JWSObject recoveredInner = nestedPayload.toJWSObject();
        assertTrue(recoveredInner.verify(new MACVerifier(HMAC_SECRET)));
        assertEquals("inner message", recoveredInner.getPayload().toString());

        PlainObject envelope = new PlainObject(
                new PlainHeader.Builder().type(new JOSEObjectType("envelope")).build(),
                nestedPayload);
        PlainObject parsedEnvelope = PlainObject.parse(envelope.serialize());
        JWSObject parsedInner = parsedEnvelope.getPayload().toJWSObject();
        assertTrue(parsedInner.verify(new MACVerifier(HMAC_SECRET)));
        assertEquals("inner message", parsedInner.getPayload().toString());

        JOSEObject parsedAsGeneric = JOSEObject.parse(inner.serialize());
        assertInstanceOf(JWSObject.class, parsedAsGeneric);
        assertEquals("signed-jose", JOSEObject.parse(inner.serialize(), new JoseKindHandler()));
    }

    @Test
    void encryptedJwtRoundTripsWithDirectAesGcmAndCompression() throws Exception {
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A128GCM)
                .type(JOSEObjectType.JWT)
                .contentType("JWT")
                .compressionAlgorithm(CompressionAlgorithm.DEF)
                .keyID("direct-key-1")
                .customParam("purpose", "confidential-claims")
                .build();
        EncryptedJWT jwt = new EncryptedJWT(header, claimsSet());

        assertEquals(JWEObject.State.UNENCRYPTED, jwt.getState());
        jwt.encrypt(new DirectEncrypter(DIRECT_ENCRYPTION_KEY));
        assertEquals(JWEObject.State.ENCRYPTED, jwt.getState());

        String serialized = jwt.serialize();
        EncryptedJWT parsed = EncryptedJWT.parse(serialized);
        assertEquals(JWEAlgorithm.DIR, parsed.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A128GCM, parsed.getHeader().getEncryptionMethod());
        assertEquals(CompressionAlgorithm.DEF, parsed.getHeader().getCompressionAlgorithm());
        assertEquals("confidential-claims", parsed.getHeader().getCustomParam("purpose"));
        assertEquals("encrypted", JWTParser.parse(serialized, new JwtKindHandler()));

        parsed.decrypt(new DirectDecrypter(DIRECT_ENCRYPTION_KEY));
        assertEquals(JWEObject.State.DECRYPTED, parsed.getState());
        assertEquals("issuer-1", parsed.getJWTClaimsSet().getIssuer());
        assertEquals(Arrays.asList("read", "write"), parsed.getJWTClaimsSet().getStringListClaim("scopes"));
    }

    @Test
    void encryptedJoseObjectRoundTripsWithAesCbcHmacContentEncryption() throws Exception {
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A128CBC_HS256)
                .type(new JOSEObjectType("jose"))
                .contentType("text/plain")
                .keyID("direct-cbc-key")
                .build();
        JWEObject jweObject = new JWEObject(header, new Payload("integrity protected secret"));

        jweObject.encrypt(new DirectEncrypter(HMAC_SECRET));
        assertEquals(JWEObject.State.ENCRYPTED, jweObject.getState());

        String serialized = jweObject.serialize();
        JWEObject parsed = JWEObject.parse(serialized);
        assertEquals(JWEAlgorithm.DIR, parsed.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A128CBC_HS256, parsed.getHeader().getEncryptionMethod());
        assertEquals("direct-cbc-key", parsed.getHeader().getKeyID());
        assertEquals("encrypted-jose", JOSEObject.parse(serialized, new JoseKindHandler()));

        parsed.decrypt(new DirectDecrypter(HMAC_SECRET));
        assertEquals(JWEObject.State.DECRYPTED, parsed.getState());
        assertEquals("integrity protected secret", parsed.getPayload().toString());
    }

    @Test
    void rsaSignaturesUseGeneratedKeysAndJwks() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaJwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("rsa-sig")
                .x509CertURL(new URL("https://example.invalid/cert.pem"))
                .x509CertThumbprint(Base64URL.encode("thumbprint"))
                .build();

        RSAKey parsedRsaJwk = RSAKey.parse(rsaJwk.toJSONString());
        assertTrue(parsedRsaJwk.isPrivate());
        assertEquals(KeyType.RSA, parsedRsaJwk.getKeyType());
        assertEquals(KeyUse.SIGNATURE, parsedRsaJwk.getKeyUse());
        assertEquals(JWSAlgorithm.RS256, parsedRsaJwk.getAlgorithm());
        assertEquals("rsa-sig", parsedRsaJwk.getKeyID());
        assertEquals(publicKey.getModulus(), parsedRsaJwk.toRSAPublicKey().getModulus());
        assertNotNull(parsedRsaJwk.toRSAPrivateKey());
        assertNotNull(parsedRsaJwk.toKeyPair());

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(parsedRsaJwk.getKeyID()).build(),
                claimsSet());
        signedJWT.sign(new RSASSASigner(parsedRsaJwk.toRSAPrivateKey()));
        SignedJWT parsedSignedJWT = SignedJWT.parse(signedJWT.serialize());
        assertTrue(parsedSignedJWT.verify(new RSASSAVerifier(parsedRsaJwk.toRSAPublicKey())));
    }

    @Test
    void jwkSetSelectorFiltersPublicPrivateAndSymmetricKeys() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        RSAKey rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("rsa-sig")
                .build();
        OctetSequenceKey octetJwk = new OctetSequenceKey.Builder(HMAC_SECRET)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.HS256)
                .keyID("hmac-sig")
                .build();
        Map<String, Object> members = new HashMap<String, Object>();
        members.put("issuer", "https://issuer.example.invalid");
        JWKSet jwkSet = new JWKSet(Arrays.asList(rsaJwk, octetJwk), members);

        JWKSet parsedSet = JWKSet.parse(jwkSet.toJSONObject(false).toString());
        assertEquals("https://issuer.example.invalid", parsedSet.getAdditionalMembers().get("issuer"));
        assertEquals(2, parsedSet.getKeys().size());
        assertArrayEquals(HMAC_SECRET, ((OctetSequenceKey) parsedSet.getKeyByKeyId("hmac-sig")).toByteArray());
        assertEquals(KeyType.OCT, JWK.parse(octetJwk.toJSONString()).getKeyType());

        JWKSelector publicRsaSelector = new JWKSelector();
        publicRsaSelector.setKeyType(KeyType.RSA);
        publicRsaSelector.setKeyUse(KeyUse.SIGNATURE);
        publicRsaSelector.setAlgorithm(JWSAlgorithm.RS256);
        publicRsaSelector.setKeyID("rsa-sig");
        publicRsaSelector.setPublicOnly(true);
        List<JWK> publicMatches = publicRsaSelector.select(jwkSet.toPublicJWKSet());
        assertEquals(1, publicMatches.size());
        assertFalse(publicMatches.get(0).isPrivate());

        JWKSelector privateSelector = new JWKSelector();
        privateSelector.setPrivateOnly(true);
        List<JWK> privateMatches = privateSelector.select(parsedSet);
        assertEquals(2, privateMatches.size());

        JWKSet publicSet = parsedSet.toPublicJWKSet();
        assertNotNull(publicSet.getKeyByKeyId("rsa-sig"));
        assertNull(publicSet.getKeyByKeyId("hmac-sig"));
    }

    @Test
    void headersAlgorithmsPayloadsAndJsonUtilitiesParseTheirPublicFormats() throws Exception {
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.parse("HS384"))
                .type(new JOSEObjectType("custom+jose"))
                .contentType("text/plain")
                .keyID("kid-384")
                .customParam("trace", "abc")
                .build();
        Header parsedGenericHeader = Header.parse(jwsHeader.toBase64URL());
        JWSHeader parsedJwsHeader = JWSHeader.parse(jwsHeader.toString());
        assertEquals(JWSAlgorithm.HS384, parsedGenericHeader.getAlgorithm());
        assertEquals("custom+jose", parsedJwsHeader.getType().getType());
        assertEquals("abc", parsedJwsHeader.getCustomParam("trace"));
        assertTrue(parsedJwsHeader.getIncludedParams().contains("kid"));

        JWEHeader jweHeader = new JWEHeader.Builder(
                JWEAlgorithm.parse("RSA-OAEP"), EncryptionMethod.parse("A128GCM"))
                .agreementPartyUInfo(Base64URL.encode("party-u"))
                .agreementPartyVInfo(Base64URL.encode("party-v"))
                .pbes2Salt(Base64URL.encode("salt"))
                .pbes2Count(4096)
                .iv(Base64URL.encode("initialization"))
                .authTag(Base64URL.encode("tag"))
                .build();
        JWEHeader parsedJweHeader = JWEHeader.parse(jweHeader.toBase64URL());
        assertEquals(EncryptionMethod.A128GCM, parsedJweHeader.getEncryptionMethod());
        assertEquals("party-u", parsedJweHeader.getAgreementPartyUInfo().decodeToString());
        assertEquals("party-v", parsedJweHeader.getAgreementPartyVInfo().decodeToString());
        assertEquals("salt", parsedJweHeader.getPBES2Salt().decodeToString());
        assertEquals(4096, parsedJweHeader.getPBES2Count());
        assertEquals("initialization", parsedJweHeader.getIV().decodeToString());
        assertEquals("tag", parsedJweHeader.getAuthTag().decodeToString());

        Payload stringPayload = new Payload("payload text");
        Payload bytesPayload = new Payload("payload text".getBytes(StandardCharsets.UTF_8));
        Payload base64Payload = new Payload(stringPayload.toBase64URL());
        assertEquals(Origin.STRING, stringPayload.getOrigin());
        assertEquals(Origin.BYTE_ARRAY, bytesPayload.getOrigin());
        assertEquals(Origin.BASE64URL, base64Payload.getOrigin());
        assertEquals("payload text", base64Payload.toString());
        assertArrayEquals("payload text".getBytes(StandardCharsets.UTF_8), bytesPayload.toBytes());

        JSONObject json = JSONObjectUtils.parseJSONObject("{\"enabled\":true,\"count\":3,"
                + "\"url\":\"https://example.invalid\",\"names\":[\"alpha\",\"beta\"],"
                + "\"nested\":{\"value\":\"ok\"}}");
        assertTrue(JSONObjectUtils.getBoolean(json, "enabled"));
        assertEquals(3, JSONObjectUtils.getInt(json, "count"));
        assertEquals(new URL("https://example.invalid"), JSONObjectUtils.getURL(json, "url"));
        assertArrayEquals(new String[] {"alpha", "beta"}, JSONObjectUtils.getStringArray(json, "names"));
        assertEquals(Arrays.asList("alpha", "beta"), JSONObjectUtils.getStringList(json, "names"));
        assertEquals("ok", JSONObjectUtils.getString(JSONObjectUtils.getJSONObject(json, "nested"), "value"));
    }

    private static JWTClaimsSet claimsSet() throws ParseException {
        JWTClaimsSet claimsSet = new JWTClaimsSet();
        claimsSet.setIssuer("issuer-1");
        claimsSet.setSubject("subject-1");
        claimsSet.setAudience(Arrays.asList("audience-a", "audience-b"));
        claimsSet.setExpirationTime(EXPIRES_AT);
        claimsSet.setNotBeforeTime(NOT_BEFORE);
        claimsSet.setIssueTime(ISSUED_AT);
        claimsSet.setJWTID("jwt-id-1");
        claimsSet.setClaim("role", "admin");
        claimsSet.setClaim("scopes", Arrays.asList("read", "write"));
        claimsSet.setClaim("active", true);
        claimsSet.setClaim("login_count", 7);
        claimsSet.setClaim("quota", 42L);
        claimsSet.setClaim("score", 1.5f);
        claimsSet.setClaim("ratio", 2.25d);
        return JWTClaimsSet.parse(claimsSet.toJSONObject());
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static final class JwtKindHandler implements JWTHandler<String> {
        @Override
        public String onPlainJWT(PlainJWT jwt) {
            return "plain";
        }

        @Override
        public String onSignedJWT(SignedJWT jwt) {
            return "signed";
        }

        @Override
        public String onEncryptedJWT(EncryptedJWT jwt) {
            return "encrypted";
        }
    }

    private static final class JoseKindHandler implements JOSEObjectHandler<String> {
        @Override
        public String onPlainObject(PlainObject plainObject) {
            return "plain-jose";
        }

        @Override
        public String onJWSObject(JWSObject jwsObject) {
            return "signed-jose";
        }

        @Override
        public String onJWEObject(JWEObject jweObject) {
            return "encrypted-jose";
        }
    }
}
