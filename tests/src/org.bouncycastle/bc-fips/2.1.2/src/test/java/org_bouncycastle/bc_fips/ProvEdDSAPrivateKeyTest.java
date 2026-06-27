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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;

import org.bouncycastle.crypto.Algorithm;
import org.bouncycastle.crypto.fips.FipsEdEC;
import org.bouncycastle.jcajce.interfaces.EdDSAPrivateKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvEdDSAPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvEdDSAPrivateKey";
    private static final String PROVIDER_JDK15_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov15EdDSAPrivateKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresEdDsaPrivateKeyEncoding() throws Exception {
        PrivateKey providerPrivateKey = newEd25519PrivateKey();
        byte[] providerSerializedKey = serialize(providerPrivateKey);
        byte[] baseSerializedKey = serializedAsBasePrivateKey(
                providerSerializedKey, providerPrivateKey.getClass().getName());

        PrivateKey basePrivateKey = assertInstanceOf(
                PrivateKey.class, deserialize(baseSerializedKey));
        EdDSAPrivateKey edDsaPrivateKey = assertInstanceOf(EdDSAPrivateKey.class, basePrivateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, basePrivateKey.getClass().getName());
        assertEquals("Ed25519", basePrivateKey.getAlgorithm());
        assertArrayEquals(providerPrivateKey.getEncoded(), basePrivateKey.getEncoded());

        byte[] wrappedSerializedKey = serializeWithEncodedKeyWrapper(
                basePrivateKey, basePrivateKey.getEncoded());
        PrivateKey deserializedKey = assertInstanceOf(
                PrivateKey.class,
                deserializeWithEncodedKeyWrapper(
                        wrappedSerializedKey, basePrivateKey.getEncoded()));
        EdDSAPrivateKey deserializedEdDsaKey = assertInstanceOf(
                EdDSAPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(basePrivateKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertArrayEquals(basePrivateKey.getEncoded(), deserializedKey.getEncoded());
        assertArrayEquals(edDsaPrivateKey.getPublicData(), deserializedEdDsaKey.getPublicData());
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

    private static byte[] serializeWithEncodedKeyWrapper(Object value, byte[] encodedKey)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (EncodedKeyWrappingObjectOutputStream objectOutput =
                new EncodedKeyWrappingObjectOutputStream(output, encodedKey)) {
            objectOutput.writeObject(value);
            assertEquals(1, objectOutput.getReplacementCount());
        }
        return output.toByteArray();
    }

    private static Object deserializeWithEncodedKeyWrapper(
            byte[] serializedValue, byte[] encodedKey) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (EncodedKeyResolvingObjectInputStream objectInput =
                new EncodedKeyResolvingObjectInputStream(input, encodedKey)) {
            Object value = objectInput.readObject();
            assertEquals(1, objectInput.getResolutionCount());
            return value;
        }
    }

    private static byte[] serializedAsBasePrivateKey(
            byte[] providerSerializedKey, String providerClassName) {
        if (PROVIDER_PRIVATE_KEY_CLASS_NAME.equals(providerClassName)) {
            return providerSerializedKey;
        }

        assertEquals(PROVIDER_JDK15_PRIVATE_KEY_CLASS_NAME, providerClassName);
        return replaceSerializedClassName(
                providerSerializedKey,
                PROVIDER_JDK15_PRIVATE_KEY_CLASS_NAME,
                PROVIDER_PRIVATE_KEY_CLASS_NAME);
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

    private static final class EncodedKeyWrappingObjectOutputStream extends ObjectOutputStream {
        private final byte[] encodedKey;
        private int replacementCount;

        EncodedKeyWrappingObjectOutputStream(ByteArrayOutputStream output, byte[] encodedKey)
                throws Exception {
            super(output);
            this.encodedKey = encodedKey.clone();
            enableReplaceObject(true);
        }

        int getReplacementCount() {
            return replacementCount;
        }

        @Override
        protected Object replaceObject(Object object) {
            if (replacementCount == 0 && object instanceof byte[]
                    && matchesEncodedKey((byte[]) object)) {
                replacementCount++;
                return FipsEdEC.Algorithm.Ed25519;
            }
            return object;
        }

        private boolean matchesEncodedKey(byte[] object) {
            if (object.length != encodedKey.length) {
                return false;
            }
            for (int i = 0; i < encodedKey.length; i++) {
                if (object[i] != encodedKey[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class EncodedKeyResolvingObjectInputStream extends ObjectInputStream {
        private final byte[] encodedKey;
        private int resolutionCount;

        EncodedKeyResolvingObjectInputStream(ByteArrayInputStream input, byte[] encodedKey)
                throws Exception {
            super(input);
            this.encodedKey = encodedKey.clone();
            enableResolveObject(true);
        }

        int getResolutionCount() {
            return resolutionCount;
        }

        @Override
        protected Object resolveObject(Object object) {
            if (object instanceof Algorithm
                    && "Ed25519".equals(((Algorithm) object).getName())) {
                resolutionCount++;
                return encodedKey.clone();
            }
            return object;
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
