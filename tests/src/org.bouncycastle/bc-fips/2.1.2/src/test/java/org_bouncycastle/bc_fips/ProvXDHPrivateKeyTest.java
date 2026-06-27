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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;

import org.bouncycastle.crypto.Algorithm;
import org.bouncycastle.crypto.general.EdEC;
import org.bouncycastle.jcajce.interfaces.XDHPrivateKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvXDHPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvXDHPrivateKey";
    private static final String PROVIDER_JDK11_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov11XDHPrivateKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void deserializationRejectsNonByteEncodedKeyObject() throws Exception {
        PrivateKey basePrivateKey = newBaseX25519PrivateKey();
        byte[] wrappedSerializedKey = serializeWithEncodedKeyWrapper(
                basePrivateKey, basePrivateKey.getEncoded());

        assertThrows(ClassCastException.class, () -> deserialize(wrappedSerializedKey));
    }

    @Test
    void serializationRoundTripRestoresXdhPrivateKeyEncoding() throws Exception {
        PrivateKey basePrivateKey = newBaseX25519PrivateKey();
        XDHPrivateKey xdhPrivateKey = assertInstanceOf(XDHPrivateKey.class, basePrivateKey);

        byte[] wrappedSerializedKey = serializeWithEncodedKeyWrapper(
                basePrivateKey, basePrivateKey.getEncoded());
        PrivateKey deserializedKey = assertInstanceOf(
                PrivateKey.class,
                deserializeWithEncodedKeyWrapper(
                        wrappedSerializedKey, basePrivateKey.getEncoded()));
        XDHPrivateKey deserializedXdhKey = assertInstanceOf(XDHPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(basePrivateKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertArrayEquals(basePrivateKey.getEncoded(), deserializedKey.getEncoded());
        assertArrayEquals(xdhPrivateKey.getPublicData(), deserializedXdhKey.getPublicData());
    }

    private static PrivateKey newBaseX25519PrivateKey() throws Exception {
        PrivateKey providerPrivateKey = newX25519PrivateKey();
        PrivateKey basePrivateKey = assertInstanceOf(
                PrivateKey.class,
                deserializeAsBasePrivateKey(
                        serialize(providerPrivateKey), providerPrivateKey.getClass().getName()));

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, basePrivateKey.getClass().getName());
        assertEquals("X25519", basePrivateKey.getAlgorithm());
        assertArrayEquals(providerPrivateKey.getEncoded(), basePrivateKey.getEncoded());
        return basePrivateKey;
    }

    private static PrivateKey newX25519PrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "X25519", bouncyCastleFipsProvider());
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

    private static Object deserializeAsBasePrivateKey(
            byte[] serializedValue, String providerClassName) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new BasePrivateKeyObjectInputStream(input, providerClassName)) {
            return objectInput.readObject();
        }
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
                return EdEC.Algorithm.X25519;
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
                    && "X25519".equals(((Algorithm) object).getName())) {
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
