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
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvECPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvECPrivateKey";
    private static final byte[] P256_PRIVATE_KEY_PKCS8 = new byte[] {
        0x30, 0x41, 0x02, 0x01, 0x00, 0x30, 0x13, 0x06,
        0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01,
        0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x03,
        0x01, 0x07, 0x04, 0x27, 0x30, 0x25, 0x02, 0x01,
        0x01, 0x04, 0x20, 0x42, 0x42, 0x42, 0x42, 0x42,
        0x42, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42,
        0x42, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42,
        0x42, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42,
        0x42, 0x42, 0x42
    };

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresGeneratedEcPrivateKey() throws Exception {
        PrivateKey privateKey = newEcPrivateKey();

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void serializationRoundTripRestoresEcPrivateKeyFromPkcs8Encoding() throws Exception {
        PrivateKey privateKey = newEcPrivateKey();
        PrivateKey pkcs8PrivateKey = ecKeyFactory().generatePrivate(
                new PKCS8EncodedKeySpec(privateKey.getEncoded()));

        assertSerializationRoundTrip(pkcs8PrivateKey);
    }

    @Test
    void serializationRoundTripRestoresNestedEcPrivateKey() throws Exception {
        PrivateKey privateKey = newEcPrivateKey();

        PrivateKeyHolder holder = assertInstanceOf(
                PrivateKeyHolder.class,
                deserialize(serialize(new PrivateKeyHolder(privateKey))));

        assertDeserializedKey(privateKey, holder.privateKey());
    }

    @Test
    void objectSerializationWritesEcPrivateKeyClassDescriptor() throws Exception {
        PrivateKey privateKey = newEcPrivateKey();

        byte[] serializedPrivateKey = serialize(privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    @Test
    void objectSerializationObservesEcPrivateKeyPayloadObjects() throws Exception {
        PrivateKey privateKey = newEcPrivateKey();
        byte[] expectedEncoding = privateKey.getEncoded();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingObjectOutputStream objectOutput = new CapturingObjectOutputStream(output);
        try (objectOutput) {
            objectOutput.writeObject(privateKey);
        }

        assertTrue(objectOutput.observedByteArray(expectedEncoding));

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        CapturingObjectInputStream objectInput = new CapturingObjectInputStream(input);
        PrivateKey restoredPrivateKey;
        try (objectInput) {
            restoredPrivateKey = assertInstanceOf(PrivateKey.class, objectInput.readObject());
        }

        assertTrue(objectInput.observedByteArray(expectedEncoding));
        assertDeserializedKey(privateKey, restoredPrivateKey);
    }

    private static void assertSerializationRoundTrip(PrivateKey privateKey) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        assertDeserializedKey(privateKey, deserialize(serialize(privateKey)));
        assertDeserializedKey(privateKey, deserializeUnshared(serializeUnshared(privateKey)));
    }

    private static void assertDeserializedKey(PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        ECPrivateKey originalEcPrivateKey = assertInstanceOf(ECPrivateKey.class, privateKey);
        ECPrivateKey ecPrivateKey = assertInstanceOf(ECPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("EC", deserializedKey.getAlgorithm());
        assertEquals(originalEcPrivateKey.getS(), ecPrivateKey.getS());
        assertEcParameterSpecEquals(originalEcPrivateKey.getParams(), ecPrivateKey.getParams());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
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

    private static final class CapturingObjectOutputStream extends ObjectOutputStream {
        private final List<Object> observedObjects = new ArrayList<>();

        CapturingObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            observedObjects.add(object);
            return object;
        }

        boolean observedByteArray(byte[] expected) {
            return observedObjects.stream()
                    .filter(byte[].class::isInstance)
                    .map(byte[].class::cast)
                    .anyMatch(actual -> Arrays.equals(expected, actual));
        }
    }

    private static final class CapturingObjectInputStream extends ObjectInputStream {
        private final List<Object> observedObjects = new ArrayList<>();

        CapturingObjectInputStream(ByteArrayInputStream input) throws IOException {
            super(input);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            observedObjects.add(object);
            return object;
        }

        boolean observedByteArray(byte[] expected) {
            return observedObjects.stream()
                    .filter(byte[].class::isInstance)
                    .map(byte[].class::cast)
                    .anyMatch(actual -> Arrays.equals(expected, actual));
        }
    }

    private static PrivateKey newEcPrivateKey() throws Exception {
        return ecKeyFactory().generatePrivate(new PKCS8EncodedKeySpec(P256_PRIVATE_KEY_PKCS8));
    }

    private static KeyFactory ecKeyFactory() throws Exception {
        return KeyFactory.getInstance("EC", bouncyCastleFipsProvider());
    }

    private record PrivateKeyHolder(PrivateKey privateKey) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return new BouncyCastleFipsProvider();
    }
}
