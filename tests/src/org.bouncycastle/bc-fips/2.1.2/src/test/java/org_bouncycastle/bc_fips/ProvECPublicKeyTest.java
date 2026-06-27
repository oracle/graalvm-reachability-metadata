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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvECPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvECPublicKey";
    private static final ECGenParameterSpec P256 = new ECGenParameterSpec("P-256");

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresEcPublicKeyFromKeySpec() throws Exception {
        PublicKey publicKey = newEcPublicKeyFromSpec();

        assertSerializationRoundTrip(publicKey);
    }

    @Test
    void serializationRoundTripRestoresEcPublicKeyFromX509Encoding() throws Exception {
        PublicKey publicKey = newEcPublicKeyFromSpec();
        PublicKey x509PublicKey = ecKeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        assertSerializationRoundTrip(x509PublicKey);
    }

    @Test
    void serializationRoundTripRestoresGeneratedEcPublicKey() throws Exception {
        PublicKey publicKey = generatedEcPublicKey();

        assertSerializationRoundTrip(publicKey);
    }

    @Test
    void serializationRoundTripRestoresNestedEcPublicKey() throws Exception {
        PublicKey publicKey = newEcPublicKeyFromSpec();

        PublicKeyHolder holder = assertInstanceOf(
                PublicKeyHolder.class,
                deserialize(serialize(new PublicKeyHolder(publicKey))));

        assertDeserializedKey(publicKey, holder.publicKey());
    }

    @Test
    void objectSerializationWritesEcPublicKeyClassDescriptor() throws Exception {
        PublicKey publicKey = newEcPublicKeyFromSpec();

        byte[] serializedPublicKey = serialize(publicKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    @Test
    void objectSerializationWritesEcPublicKeyEncodedPayload() throws Exception {
        PublicKey publicKey = newEcPublicKeyFromSpec();

        byte[] serializedPublicKey = serialize(publicKey);

        assertContainsSubsequence(serializedPublicKey, publicKey.getEncoded());
    }

    @Test
    void serializationRoundTripWithExplicitClassResolverRestoresEcPublicKey()
            throws Exception {
        PublicKey publicKey = newEcPublicKeyFromSpec();

        Object restoredPublicKey = deserializeWithProviderClassResolver(
                serialize(publicKey), publicKey.getClass());

        assertDeserializedKey(publicKey, restoredPublicKey);
    }

    private static void assertSerializationRoundTrip(PublicKey publicKey) throws Exception {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        assertDeserializedKey(publicKey, deserialize(serialize(publicKey)));
        assertDeserializedKey(publicKey, deserializeUnshared(serializeUnshared(publicKey)));
    }

    private static void assertDeserializedKey(PublicKey publicKey, Object deserializedValue) {
        PublicKey deserializedKey = assertInstanceOf(PublicKey.class, deserializedValue);
        ECPublicKey originalEcPublicKey = assertInstanceOf(ECPublicKey.class, publicKey);
        ECPublicKey ecPublicKey = assertInstanceOf(ECPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("EC", deserializedKey.getAlgorithm());
        assertEquals("X.509", deserializedKey.getFormat());
        assertEquals(originalEcPublicKey.getW(), ecPublicKey.getW());
        assertEcParameterSpecEquals(originalEcPublicKey.getParams(), ecPublicKey.getParams());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static void assertEcParameterSpecEquals(
            ECParameterSpec expected, ECParameterSpec actual) {
        assertEquals(expected.getCurve(), actual.getCurve());
        assertEquals(expected.getGenerator(), actual.getGenerator());
        assertEquals(expected.getOrder(), actual.getOrder());
        assertEquals(expected.getCofactor(), actual.getCofactor());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static byte[] serializeUnshared(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeUnshared(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return objectInput.readObject();
        }
    }

    private static Object deserializeUnshared(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return objectInput.readUnshared();
        }
    }

    private static Object deserializeWithProviderClassResolver(
            byte[] serializedValue, Class<?> providerKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ProviderClassResolvingObjectInputStream(
                input, providerKeyClass)) {
            return objectInput.readObject();
        }
    }

    private static void assertContainsSubsequence(byte[] container, byte[] subsequence) {
        for (int offset = 0; offset <= container.length - subsequence.length; offset++) {
            if (containsSubsequenceAt(container, subsequence, offset)) {
                return;
            }
        }
        throw new AssertionError("Serialized stream does not contain the public key encoding");
    }

    private static boolean containsSubsequenceAt(
            byte[] container, byte[] subsequence, int offset) {
        for (int index = 0; index < subsequence.length; index++) {
            if (container[offset + index] != subsequence[index]) {
                return false;
            }
        }
        return true;
    }

    private static PublicKey newEcPublicKeyFromSpec() throws Exception {
        ECParameterSpec parameterSpec = ecParameterSpec();
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(
                parameterSpec.getGenerator(), parameterSpec);
        return ecKeyFactory().generatePublic(publicKeySpec);
    }

    private static PublicKey generatedEcPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "EC", bouncyCastleFipsProvider());
        keyPairGenerator.initialize(P256, new SecureRandom(new byte[] {1, 2, 3, 4}));
        return keyPairGenerator.generateKeyPair().getPublic();
    }

    private static ECParameterSpec ecParameterSpec() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance(
                "EC", bouncyCastleFipsProvider());
        parameters.init(P256);
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static KeyFactory ecKeyFactory() throws Exception {
        return KeyFactory.getInstance("EC", bouncyCastleFipsProvider());
    }

    private record PublicKeyHolder(PublicKey publicKey) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static final class ProviderClassResolvingObjectInputStream
            extends ObjectInputStream {
        private final Class<?> providerKeyClass;

        ProviderClassResolvingObjectInputStream(
                ByteArrayInputStream input, Class<?> providerKeyClass) throws IOException {
            super(input);
            this.providerKeyClass = providerKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PUBLIC_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return providerKeyClass;
            }
            return super.resolveClass(descriptor);
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
