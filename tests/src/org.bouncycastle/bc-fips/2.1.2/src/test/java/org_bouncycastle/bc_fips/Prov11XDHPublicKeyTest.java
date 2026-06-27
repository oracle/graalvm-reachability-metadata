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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

import org.bouncycastle.jcajce.interfaces.XDHPublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Prov11XDHPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov11XDHPublicKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresGeneratedX25519PublicKeyEncoding() throws Exception {
        KeyPair keyPair = generateKeyPair("X25519");

        assertSerializationRoundTrip(keyPair.getPublic(), "X25519", NamedParameterSpec.X25519);
    }

    @Test
    void serializationRoundTripRestoresGeneratedX448PublicKeyEncoding() throws Exception {
        KeyPair keyPair = generateKeyPair("X448");

        assertSerializationRoundTrip(keyPair.getPublic(), "X448", NamedParameterSpec.X448);
    }

    @Test
    void serializationRoundTripRestoresKeyFactoryX25519PublicKeyEncoding() throws Exception {
        assertKeyFactoryPublicKeySerializationRoundTrip("X25519", NamedParameterSpec.X25519);
    }

    @Test
    void serializationRoundTripRestoresKeyFactoryX448PublicKeyEncoding() throws Exception {
        assertKeyFactoryPublicKeySerializationRoundTrip("X448", NamedParameterSpec.X448);
    }

    @Test
    void deserializedX25519PublicKeyCanBeUsedForAgreement() throws Exception {
        assertDeserializedPublicKeyCanBeUsedForAgreement("X25519");
    }

    @Test
    void deserializedX448PublicKeyCanBeUsedForAgreement() throws Exception {
        assertDeserializedPublicKeyCanBeUsedForAgreement("X448");
    }

    private static void assertKeyFactoryPublicKeySerializationRoundTrip(
            String algorithm, NamedParameterSpec expectedParameters) throws Exception {
        PublicKey generatedPublicKey = generateKeyPair(algorithm).getPublic();
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm, bouncyCastleFipsProvider());
        PublicKey publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(generatedPublicKey.getEncoded()));

        assertSerializationRoundTrip(publicKey, algorithm, expectedParameters);
    }

    private static void assertDeserializedPublicKeyCanBeUsedForAgreement(
            String algorithm) throws Exception {
        KeyPair localKeyPair = generateKeyPair(algorithm);
        KeyPair peerKeyPair = generateKeyPair(algorithm);
        XDHPublicKey publicKey = assertInstanceOf(XDHPublicKey.class, peerKeyPair.getPublic());
        PublicKey deserializedPublicKey = assertInstanceOf(
                PublicKey.class, deserialize(serialize(publicKey), publicKey.getClass()));

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedPublicKey.getClass().getName());
        assertArrayEquals(
                agreementSecret(algorithm, localKeyPair.getPrivate(), peerKeyPair.getPublic()),
                agreementSecret(algorithm, localKeyPair.getPrivate(), deserializedPublicKey));
    }

    private static byte[] agreementSecret(
            String algorithm, PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance(algorithm, bouncyCastleFipsProvider());
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static KeyPair generateKeyPair(String algorithm) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                algorithm, bouncyCastleFipsProvider());
        return keyPairGenerator.generateKeyPair();
    }

    private static void assertSerializationRoundTrip(
            PublicKey publicKey,
            String algorithm,
            NamedParameterSpec expectedParameters) throws Exception {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        PublicKey deserializedKey = assertInstanceOf(
                PublicKey.class, deserialize(serialize(publicKey), publicKey.getClass()));
        XECPublicKey xecPublicKey = assertInstanceOf(XECPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(algorithm, deserializedKey.getAlgorithm());
        assertEquals(expectedParameters, xecPublicKey.getParams());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(
            byte[] serializedValue, Class<?> publicKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new PublicKeyObjectInputStream(input, publicKeyClass)) {
            return objectInput.readObject();
        }
    }

    private static final class PublicKeyObjectInputStream extends ObjectInputStream {
        private final Class<?> publicKeyClass;

        PublicKeyObjectInputStream(
                ByteArrayInputStream input, Class<?> publicKeyClass) throws Exception {
            super(input);
            this.publicKeyClass = publicKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PUBLIC_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return publicKeyClass;
            }
            return super.resolveClass(descriptor);
        }
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
