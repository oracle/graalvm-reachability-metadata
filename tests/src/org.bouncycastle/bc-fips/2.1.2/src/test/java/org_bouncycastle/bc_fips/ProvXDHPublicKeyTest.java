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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.jcajce.interfaces.XDHPrivateKey;
import org.bouncycastle.jcajce.interfaces.XDHPublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvXDHPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvXDHPublicKey";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvXDHPrivateKey";
    private static final String PROVIDER_JDK11_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov11XDHPrivateKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresX25519PublicKeyEncoding() throws Exception {
        PublicKey basePublicKey = newBasePublicKey("X25519");

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, basePublicKey.getClass().getName());
        assertEquals("X25519", basePublicKey.getAlgorithm());

        assertSerializationRoundTrip(basePublicKey);
    }

    @Test
    void serializationRoundTripRestoresX448PublicKeyEncoding() throws Exception {
        PublicKey basePublicKey = newBasePublicKey("X448");

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, basePublicKey.getClass().getName());
        assertEquals("X448", basePublicKey.getAlgorithm());

        assertSerializationRoundTrip(basePublicKey);
    }

    @Test
    void serializationHooksReadAndWriteSeededPublicKeyEncoding() throws Exception {
        byte[] encodedPublicKey = x509X25519();
        byte[] seededPublicKey = replaceSerializedClassName(
                serialize(new PublicKeySerializationSeed(encodedPublicKey)),
                PublicKeySerializationSeed.class.getName(),
                PROVIDER_PUBLIC_KEY_CLASS_NAME);
        PublicKey deserializedSeed = assertInstanceOf(
                PublicKey.class, deserialize(seededPublicKey));

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedSeed.getClass().getName());
        assertEquals("X25519", deserializedSeed.getAlgorithm());
        assertArrayEquals(encodedPublicKey, deserializedSeed.getEncoded());

        assertSerializationRoundTrip(deserializedSeed);
    }

    private static void assertSerializationRoundTrip(PublicKey basePublicKey) throws Exception {
        XDHPublicKey xdhPublicKey = assertInstanceOf(XDHPublicKey.class, basePublicKey);
        byte[] serializedKey = serializeCapturingEncodedPayload(
                basePublicKey, basePublicKey.getEncoded());
        PublicKey deserializedKey = assertInstanceOf(
                PublicKey.class,
                deserializeCapturingEncodedPayload(serializedKey, basePublicKey.getEncoded()));
        XDHPublicKey deserializedXdhKey = assertInstanceOf(XDHPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(basePublicKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertArrayEquals(basePublicKey.getEncoded(), deserializedKey.getEncoded());
        assertArrayEquals(xdhPublicKey.getPublicData(), deserializedXdhKey.getPublicData());
    }

    private static PublicKey newBasePublicKey(String algorithm) throws Exception {
        PrivateKey privateKey = newBasePrivateKey(algorithm);
        XDHPrivateKey xdhPrivateKey = assertInstanceOf(XDHPrivateKey.class, privateKey);
        return assertInstanceOf(PublicKey.class, xdhPrivateKey.getPublicKey());
    }

    private static PrivateKey newBasePrivateKey(String algorithm) throws Exception {
        PrivateKey providerPrivateKey = newPrivateKey(algorithm);
        PrivateKey basePrivateKey = assertInstanceOf(
                PrivateKey.class,
                deserializeAsBasePrivateKey(
                        serialize(providerPrivateKey), providerPrivateKey.getClass().getName()));

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, basePrivateKey.getClass().getName());
        assertEquals(algorithm, basePrivateKey.getAlgorithm());
        assertArrayEquals(providerPrivateKey.getEncoded(), basePrivateKey.getEncoded());
        return basePrivateKey;
    }

    private static PrivateKey newPrivateKey(String algorithm) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                algorithm, bouncyCastleFipsProvider());
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

    private static Object deserializeAsBasePrivateKey(
            byte[] serializedValue, String providerClassName) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new BasePrivateKeyObjectInputStream(input, providerClassName)) {
            return objectInput.readObject();
        }
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

    private static byte[] x509X25519() {
        byte[] prefix = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
            0x6e, 0x03, 0x21, 0x00
        };
        byte[] publicKeyBytes = new byte[] {
            0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        return concatenate(prefix, publicKeyBytes);
    }

    private static byte[] concatenate(byte[] prefix, byte[] suffix) {
        byte[] value = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, value, 0, prefix.length);
        System.arraycopy(suffix, 0, value, prefix.length, suffix.length);
        return value;
    }

    private static final class BasePrivateKeyObjectInputStream extends ObjectInputStream {
        private final String providerClassName;

        BasePrivateKeyObjectInputStream(
                ByteArrayInputStream input, String providerClassName) throws Exception {
            super(input);
            this.providerClassName = providerClassName;
        }

        @Override
        protected ObjectStreamClass readClassDescriptor()
                throws IOException, ClassNotFoundException {
            ObjectStreamClass descriptor = super.readClassDescriptor();
            if (PROVIDER_PRIVATE_KEY_CLASS_NAME.equals(providerClassName)) {
                return descriptor;
            }
            assertEquals(PROVIDER_JDK11_PRIVATE_KEY_CLASS_NAME, providerClassName);
            if (PROVIDER_JDK11_PRIVATE_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return ObjectStreamClass.lookup(Class.forName(PROVIDER_PRIVATE_KEY_CLASS_NAME));
            }
            return descriptor;
        }
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
        return new BouncyCastleFipsProvider();
    }
}
