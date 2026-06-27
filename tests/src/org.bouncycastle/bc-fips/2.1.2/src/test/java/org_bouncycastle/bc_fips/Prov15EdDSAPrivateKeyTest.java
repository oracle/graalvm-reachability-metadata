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
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Prov15EdDSAPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov15EdDSAPrivateKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresEd25519PrivateKeyEncoding() throws Exception {
        byte[] privateKeyBytes = privateKeyBytes(32);

        assertSerializationRoundTrip(
                privateKeyFromSpec("Ed25519", NamedParameterSpec.ED25519, privateKeyBytes),
                "Ed25519", NamedParameterSpec.ED25519);
        assertSerializationRoundTrip(
                privateKeyFromPkcs8("Ed25519", pkcs8Ed25519(privateKeyBytes)),
                "Ed25519", NamedParameterSpec.ED25519);
    }

    @Test
    void serializationRoundTripRestoresEd448PrivateKeyEncoding() throws Exception {
        byte[] privateKeyBytes = privateKeyBytes(57);

        assertSerializationRoundTrip(
                privateKeyFromSpec("Ed448", NamedParameterSpec.ED448, privateKeyBytes),
                "Ed448", NamedParameterSpec.ED448);
        assertSerializationRoundTrip(
                privateKeyFromPkcs8("Ed448", pkcs8Ed448(privateKeyBytes)),
                "Ed448", NamedParameterSpec.ED448);
    }

    private static PrivateKey privateKeyFromSpec(
            String algorithm,
            NamedParameterSpec parameters,
            byte[] privateKeyBytes) throws Exception {
        return KeyFactory.getInstance(algorithm, bouncyCastleFipsProvider())
                .generatePrivate(new EdECPrivateKeySpec(parameters, privateKeyBytes));
    }

    private static PrivateKey privateKeyFromPkcs8(
            String algorithm, byte[] encodedPrivateKey) throws Exception {
        return KeyFactory.getInstance(algorithm, bouncyCastleFipsProvider())
                .generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));
    }

    private static void assertSerializationRoundTrip(
            PrivateKey privateKey,
            String algorithm,
            NamedParameterSpec expectedParameters) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        assertDeserializedKey(privateKey, deserialize(serialize(privateKey)), algorithm,
                expectedParameters);
        assertDeserializedKey(privateKey, deserializeUnshared(serializeUnshared(privateKey)),
                algorithm, expectedParameters);
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey,
            Object deserializedValue,
            String algorithm,
            NamedParameterSpec expectedParameters) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        EdECPrivateKey edEcPrivateKey = assertInstanceOf(EdECPrivateKey.class, deserializedKey);
        EdECPrivateKey originalEdEcPrivateKey = assertInstanceOf(EdECPrivateKey.class, privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(algorithm, deserializedKey.getAlgorithm());
        assertEquals(expectedParameters, edEcPrivateKey.getParams());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
        assertArrayEquals(
                originalEdEcPrivateKey.getBytes().orElseThrow(),
                edEcPrivateKey.getBytes().orElseThrow());
    }

    private static byte[] privateKeyBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return bytes;
    }

    private static byte[] pkcs8Ed25519(byte[] privateKeyBytes) {
        byte[] prefix = new byte[] {
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
            0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
        };
        return concatenate(prefix, privateKeyBytes);
    }

    private static byte[] pkcs8Ed448(byte[] privateKeyBytes) {
        byte[] prefix = new byte[] {
            0x30, 0x47, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
            0x03, 0x2b, 0x65, 0x71, 0x04, 0x3b, 0x04, 0x39
        };
        return concatenate(prefix, privateKeyBytes);
    }

    private static byte[] concatenate(byte[] prefix, byte[] suffix) {
        byte[] value = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, value, 0, prefix.length);
        System.arraycopy(suffix, 0, value, prefix.length, suffix.length);
        return value;
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

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
