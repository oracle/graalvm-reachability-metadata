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
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import org.bouncycastle.jcajce.interfaces.EdDSAPrivateKey;
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvEdDSAPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvEdDSAPublicKey";
    private static final String PROVIDER_JDK15_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov15EdDSAPublicKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresEdDsaPublicKeyEncoding() throws Exception {
        PublicKey providerPublicKey = publicKeyFromX509();
        PublicKey basePublicKey = publicKeySerializedAsBaseClass(providerPublicKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, basePublicKey.getClass().getName());
        assertEquals("Ed25519", basePublicKey.getAlgorithm());
        assertArrayEquals(providerPublicKey.getEncoded(), basePublicKey.getEncoded());

        assertSerializationRoundTrip(basePublicKey);
    }

    @Test
    void serializationRoundTripRestoresPublicKeyReturnedByBasePrivateKey() throws Exception {
        EdDSAPrivateKey basePrivateKey = privateKeySerializedAsBaseClass();
        PublicKey basePublicKey = assertInstanceOf(PublicKey.class, basePrivateKey.getPublicKey());

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, basePublicKey.getClass().getName());
        assertEquals("Ed25519", basePublicKey.getAlgorithm());

        assertSerializationRoundTrip(basePublicKey);
    }

    @Test
    void serializationHooksReadAndWriteSeededPublicKeyEncoding() throws Exception {
        byte[] encodedPublicKey = x509Ed25519();
        byte[] seededPublicKey = replaceSerializedClassName(
                serialize(new PublicKeySerializationSeed(encodedPublicKey)),
                PublicKeySerializationSeed.class.getName(),
                PROVIDER_PUBLIC_KEY_CLASS_NAME);
        PublicKey basePublicKey = assertInstanceOf(PublicKey.class, deserialize(seededPublicKey));

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, basePublicKey.getClass().getName());
        assertEquals("Ed25519", basePublicKey.getAlgorithm());
        assertArrayEquals(encodedPublicKey, basePublicKey.getEncoded());

        assertSerializationRoundTrip(basePublicKey);
    }

    private static void assertSerializationRoundTrip(PublicKey basePublicKey) throws Exception {
        EdDSAPublicKey baseEdDsaPublicKey = assertInstanceOf(EdDSAPublicKey.class, basePublicKey);
        byte[] serializedKey = serializeCapturingEncodedPayload(
                basePublicKey, basePublicKey.getEncoded());
        PublicKey deserializedKey = assertInstanceOf(
                PublicKey.class,
                deserializeCapturingEncodedPayload(serializedKey, basePublicKey.getEncoded()));
        EdDSAPublicKey deserializedEdDsaKey = assertInstanceOf(
                EdDSAPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(basePublicKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertArrayEquals(basePublicKey.getEncoded(), deserializedKey.getEncoded());
        assertArrayEquals(
                baseEdDsaPublicKey.getPublicData(), deserializedEdDsaKey.getPublicData());
    }

    private static PublicKey publicKeyFromX509() throws Exception {
        return KeyFactory.getInstance("Ed25519", bouncyCastleFipsProvider())
                .generatePublic(new X509EncodedKeySpec(x509Ed25519()));
    }

    private static PublicKey publicKeySerializedAsBaseClass(PublicKey providerPublicKey)
            throws Exception {
        byte[] providerSerializedKey = serialize(providerPublicKey);
        byte[] baseSerializedKey = serializedAsBaseClass(
                providerSerializedKey,
                providerPublicKey.getClass().getName(),
                PROVIDER_JDK15_PUBLIC_KEY_CLASS_NAME,
                PROVIDER_PUBLIC_KEY_CLASS_NAME);
        return assertInstanceOf(PublicKey.class, deserialize(baseSerializedKey));
    }

    private static EdDSAPrivateKey privateKeySerializedAsBaseClass() throws Exception {
        PrivateKey providerPrivateKey = newEd25519PrivateKey();
        byte[] providerSerializedKey = serialize(providerPrivateKey);
        byte[] baseSerializedKey = serializedAsBaseClass(
                providerSerializedKey,
                providerPrivateKey.getClass().getName(),
                "org.bouncycastle.jcajce.provider.Prov15EdDSAPrivateKey",
                "org.bouncycastle.jcajce.provider.ProvEdDSAPrivateKey");
        return assertInstanceOf(EdDSAPrivateKey.class, deserialize(baseSerializedKey));
    }

    private static PrivateKey newEd25519PrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "Ed25519", bouncyCastleFipsProvider());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return keyPair.getPrivate();
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return objectInput.readObject();
        }
    }

    private static byte[] serializeCapturingEncodedPayload(Object value, byte[] expectedPayload)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PayloadCapturingObjectOutputStream objectOutput =
                new PayloadCapturingObjectOutputStream(output, expectedPayload)) {
            objectOutput.writeObject(value);
            assertTrue(objectOutput.sawExpectedPayload());
        }
        return output.toByteArray();
    }

    private static Object deserializeCapturingEncodedPayload(
            byte[] serializedValue, byte[] expectedPayload) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (PayloadCapturingObjectInputStream objectInput =
                new PayloadCapturingObjectInputStream(input, expectedPayload)) {
            Object value = objectInput.readObject();
            assertTrue(objectInput.sawExpectedPayload());
            return value;
        }
    }

    private static byte[] serializedAsBaseClass(
            byte[] providerSerializedKey,
            String providerClassName,
            String jdk15ClassName,
            String baseClassName) {
        if (baseClassName.equals(providerClassName)) {
            return providerSerializedKey;
        }

        assertEquals(jdk15ClassName, providerClassName);
        return replaceSerializedClassName(providerSerializedKey, jdk15ClassName, baseClassName);
    }

    private static byte[] replaceSerializedClassName(
            byte[] serializedValue, String sourceClassName, String targetClassName) {
        byte[] sourceName = sourceClassName.getBytes(StandardCharsets.UTF_8);
        byte[] targetName = targetClassName.getBytes(StandardCharsets.UTF_8);
        int sourceNameOffset = findUniqueOffset(serializedValue, sourceName);
        int lengthOffset = sourceNameOffset - 2;

        assertEquals(sourceName.length, readUnsignedShort(serializedValue, lengthOffset));

        ByteArrayOutputStream output = new ByteArrayOutputStream(
                serializedValue.length - sourceName.length + targetName.length);
        output.write(serializedValue, 0, lengthOffset);
        output.write((targetName.length >>> 8) & 0xff);
        output.write(targetName.length & 0xff);
        output.write(targetName, 0, targetName.length);
        output.write(
                serializedValue,
                sourceNameOffset + sourceName.length,
                serializedValue.length - sourceNameOffset - sourceName.length);
        return output.toByteArray();
    }

    private static int findUniqueOffset(byte[] value, byte[] target) {
        int result = -1;
        int matches = 0;
        for (int i = 0; i <= value.length - target.length; i++) {
            if (matchesAt(value, target, i)) {
                result = i;
                matches++;
            }
        }
        assertEquals(1, matches);
        return result;
    }

    private static boolean matchesAt(byte[] value, byte[] target, int offset) {
        for (int i = 0; i < target.length; i++) {
            if (value[offset + i] != target[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readUnsignedShort(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 8) | (value[offset + 1] & 0xff);
    }

    private static byte[] x509Ed25519() {
        byte[] prefix = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
            0x70, 0x03, 0x21, 0x00
        };
        byte[] publicKeyBytes = new byte[] {
            (byte) 0xd7, 0x5a, (byte) 0x98, 0x01, (byte) 0x82, (byte) 0xb1, 0x0a,
            (byte) 0xb7, (byte) 0xd5, 0x4b, (byte) 0xfe, (byte) 0xd3, (byte) 0xc9,
            0x64, 0x07, 0x3a, 0x0e, (byte) 0xe1, 0x72, (byte) 0xf3, (byte) 0xda,
            (byte) 0xa6, 0x23, 0x25, (byte) 0xaf, 0x02, 0x1a, 0x68, (byte) 0xf7,
            0x07, 0x51, 0x1a
        };
        return concatenate(prefix, publicKeyBytes);
    }

    private static byte[] concatenate(byte[] prefix, byte[] suffix) {
        byte[] value = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, value, 0, prefix.length);
        System.arraycopy(suffix, 0, value, prefix.length, suffix.length);
        return value;
    }

    private static final class PublicKeySerializationSeed implements Serializable {
        private static final long serialVersionUID = 1L;

        private final transient byte[] encodedPublicKey;

        PublicKeySerializationSeed(byte[] encodedPublicKey) {
            this.encodedPublicKey = encodedPublicKey.clone();
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeObject(encodedPublicKey);
        }
    }

    private static final class PayloadCapturingObjectOutputStream extends ObjectOutputStream {
        private final byte[] expectedPayload;
        private boolean sawExpectedPayload;

        PayloadCapturingObjectOutputStream(
                ByteArrayOutputStream output, byte[] expectedPayload) throws Exception {
            super(output);
            this.expectedPayload = expectedPayload.clone();
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object value) {
            if (value instanceof byte[] payload && Arrays.equals(expectedPayload, payload)) {
                sawExpectedPayload = true;
            }
            return value;
        }

        boolean sawExpectedPayload() {
            return sawExpectedPayload;
        }
    }

    private static final class PayloadCapturingObjectInputStream extends ObjectInputStream {
        private final byte[] expectedPayload;
        private boolean sawExpectedPayload;

        PayloadCapturingObjectInputStream(
                ByteArrayInputStream input, byte[] expectedPayload) throws Exception {
            super(input);
            this.expectedPayload = expectedPayload.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object value) {
            if (value instanceof byte[] payload && Arrays.equals(expectedPayload, payload)) {
                sawExpectedPayload = true;
            }
            return value;
        }

        boolean sawExpectedPayload() {
            return sawExpectedPayload;
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
