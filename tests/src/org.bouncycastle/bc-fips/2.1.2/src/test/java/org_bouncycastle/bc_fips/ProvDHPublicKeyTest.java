/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvDHPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvDHPublicKey";
    private static final BigInteger MODP_2048_PRIME = new BigInteger("""
            FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1
            29024E088A67CC74020BBEA63B139B22514A08798E3404DD
            EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245
            E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED
            EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D
            C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F
            83655D23DCA3AD961C62F356208552BB9ED529077096966D
            670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B
            E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9
            DE2BCBF6955817183995497CEA956AE515D2261898FA0510
            15728E5A8AACAA68FFFFFFFFFFFFFFFF
            """.replaceAll("\\s", ""), 16);
    private static final BigInteger GENERATOR = BigInteger.TWO;
    private static final BigInteger PRIVATE_EXPONENT = new BigInteger(
            "1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF", 16);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationHookRoundTripRestoresDhPublicKeyFromKeySpec() throws Throwable {
        PublicKey publicKey = dhKeyFactory().generatePublic(publicKeySpec(PRIVATE_EXPONENT));

        assertSerializationHookRoundTrip(publicKey);
    }

    @Test
    void objectSerializationWritesDhPublicKeyClassDescriptor() throws Exception {
        PublicKey publicKey = dhKeyFactory().generatePublic(publicKeySpec(PRIVATE_EXPONENT));

        byte[] serializedPublicKey = serialize(publicKey);

        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    @Test
    void serializationHookRoundTripRestoresDhPublicKeyFromX509Encoding() throws Throwable {
        PublicKey publicKey = dhKeyFactory().generatePublic(publicKeySpec(PRIVATE_EXPONENT));
        PublicKey x509PublicKey = dhKeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        assertSerializationHookRoundTrip(x509PublicKey);
    }

    @Test
    void serializationHookRoundTripRestoresGeneratedDhPublicKey() throws Throwable {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "DH", bouncyCastleFipsProvider());
        keyPairGenerator.initialize(new DHParameterSpec(MODP_2048_PRIME, GENERATOR));

        assertSerializationHookRoundTrip(keyPairGenerator.generateKeyPair().getPublic());
    }

    @Test
    void dhPublicKeyRestoredByReadHookCanBeUsedForAgreement() throws Throwable {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "DH", bouncyCastleFipsProvider());
        keyPairGenerator.initialize(new DHParameterSpec(MODP_2048_PRIME, GENERATOR));
        KeyPair localKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair peerKeyPair = keyPairGenerator.generateKeyPair();
        PublicKey restoredPublicKey = restoreWithSerializationHooks(peerKeyPair.getPublic());

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, restoredPublicKey.getClass().getName());
        assertArrayEquals(
                agreementSecret(localKeyPair.getPrivate(), peerKeyPair.getPublic()),
                agreementSecret(localKeyPair.getPrivate(), restoredPublicKey));
    }

    private static void assertSerializationHookRoundTrip(PublicKey publicKey) throws Throwable {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        PublicKey restoredPublicKey = restoreWithSerializationHooks(publicKey);

        assertDeserializedKey(publicKey, restoredPublicKey);
    }

    private static PublicKey restoreWithSerializationHooks(PublicKey publicKey) throws Throwable {
        PublicKey targetKey = dhKeyFactory().generatePublic(
                publicKeySpec(PRIVATE_EXPONENT.add(BigInteger.ONE)));
        assertNotEquals(
                assertInstanceOf(DHPublicKey.class, publicKey).getY(),
                assertInstanceOf(DHPublicKey.class, targetKey).getY());

        byte[] hookPayload = writeUsingSerializationHook(publicKey);
        readUsingSerializationHook(targetKey, hookPayload);

        return targetKey;
    }

    private static byte[] writeUsingSerializationHook(PublicKey publicKey) throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new NoDefaultWriteObjectOutputStream(output)) {
            serializationHook(publicKey.getClass(), "writeObject", ObjectOutputStream.class)
                    .invoke(publicKey, objectOutput);
        }
        return output.toByteArray();
    }

    private static void readUsingSerializationHook(PublicKey publicKey, byte[] hookPayload)
            throws Throwable {
        ByteArrayInputStream input = new ByteArrayInputStream(hookPayload);
        try (ObjectInputStream objectInput = new NoDefaultReadObjectInputStream(input)) {
            serializationHook(publicKey.getClass(), "readObject", ObjectInputStream.class)
                    .invoke(publicKey, objectInput);
        }
    }

    private static MethodHandle serializationHook(
            Class<?> keyClass, String methodName, Class<?> parameterType) throws Exception {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(
                keyClass, MethodHandles.lookup());
        return privateLookup.findSpecial(
                keyClass, methodName, MethodType.methodType(void.class, parameterType), keyClass);
    }

    private static void assertDeserializedKey(
            PublicKey publicKey, Object deserializedValue) {
        PublicKey deserializedKey = assertInstanceOf(PublicKey.class, deserializedValue);
        DHPublicKey originalDhPublicKey = assertInstanceOf(DHPublicKey.class, publicKey);
        DHPublicKey dhPublicKey = assertInstanceOf(DHPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("DH", deserializedKey.getAlgorithm());
        assertEquals(originalDhPublicKey.getY(), dhPublicKey.getY());
        assertEquals(MODP_2048_PRIME, dhPublicKey.getParams().getP());
        assertEquals(GENERATOR, dhPublicKey.getParams().getG());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] agreementSecret(
            PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("DH", bouncyCastleFipsProvider());
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static DHPublicKeySpec publicKeySpec(BigInteger privateExponent) {
        return new DHPublicKeySpec(
                GENERATOR.modPow(privateExponent, MODP_2048_PRIME),
                MODP_2048_PRIME,
                GENERATOR);
    }

    private static KeyFactory dhKeyFactory() throws Exception {
        return KeyFactory.getInstance("DH", bouncyCastleFipsProvider());
    }

    private static final class NoDefaultWriteObjectOutputStream extends ObjectOutputStream {
        NoDefaultWriteObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
        }

        @Override
        public void defaultWriteObject() {
            // `ProvDHPublicKey` stores its state through explicit `writeObject` calls.
        }
    }

    private static final class NoDefaultReadObjectInputStream extends ObjectInputStream {
        NoDefaultReadObjectInputStream(ByteArrayInputStream input) throws IOException {
            super(input);
        }

        @Override
        public void defaultReadObject() {
            // `ProvDHPublicKey` restores its state through explicit `readObject` calls.
        }
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return new BouncyCastleFipsProvider();
    }
}
