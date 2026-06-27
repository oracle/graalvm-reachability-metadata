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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Prov15EdDSAPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov15EdDSAPublicKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresEd25519PublicKeyEncoding() throws Exception {
        PublicKey publicKey = publicKeyFromX509("Ed25519", x509Ed25519());

        assertSerializationRoundTrip(publicKey, "Ed25519", NamedParameterSpec.ED25519);
    }

    @Test
    void serializationRoundTripRestoresEd448PublicKeyEncoding() throws Exception {
        PublicKey publicKey = publicKeyFromX509("Ed448", x509Ed448());

        assertSerializationRoundTrip(publicKey, "Ed448", NamedParameterSpec.ED448);
    }


    private static PublicKey publicKeyFromX509(
            String algorithm, byte[] encodedPublicKey) throws Exception {
        return KeyFactory.getInstance(algorithm, bouncyCastleFipsProvider())
                .generatePublic(new X509EncodedKeySpec(encodedPublicKey));
    }


    private static void assertSerializationRoundTrip(
            PublicKey publicKey,
            String algorithm,
            NamedParameterSpec expectedParameters) throws Exception {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        assertDeserializedKey(publicKey, deserialize(serialize(publicKey)), algorithm,
                expectedParameters);
        assertDeserializedKey(publicKey, deserializeUnshared(serializeUnshared(publicKey)),
                algorithm, expectedParameters);
    }

    private static void assertDeserializedKey(
            PublicKey publicKey,
            Object deserializedValue,
            String algorithm,
            NamedParameterSpec expectedParameters) {
        PublicKey deserializedKey = assertInstanceOf(PublicKey.class, deserializedValue);
        EdECPublicKey edEcPublicKey = assertInstanceOf(EdECPublicKey.class, deserializedKey);
        EdDSAPublicKey edDsaPublicKey = assertInstanceOf(EdDSAPublicKey.class, deserializedKey);
        EdDSAPublicKey originalEdDsaPublicKey = assertInstanceOf(EdDSAPublicKey.class, publicKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(algorithm, deserializedKey.getAlgorithm());
        assertEquals(expectedParameters, edEcPublicKey.getParams());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
        assertArrayEquals(originalEdDsaPublicKey.getPublicData(), edDsaPublicKey.getPublicData());
    }

    private static byte[] x509Ed25519() {
        byte[] prefix = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
            0x70, 0x03, 0x21, 0x00
        };
        byte[] publicKeyBytes = new byte[] {
            (byte) 0xd7, 0x5a, (byte) 0x98, 0x01, (byte) 0x82, (byte) 0xb1, 0x0a, (byte) 0xb7,
            (byte) 0xd5, 0x4b, (byte) 0xfe, (byte) 0xd3, (byte) 0xc9, 0x64, 0x07, 0x3a,
            0x0e, (byte) 0xe1, 0x72, (byte) 0xf3, (byte) 0xda, (byte) 0xa6, 0x23, 0x25,
            (byte) 0xaf, 0x02, 0x1a, 0x68, (byte) 0xf7, 0x07, 0x51, 0x1a
        };
        return concatenate(prefix, publicKeyBytes);
    }

    private static byte[] x509Ed448() {
        byte[] prefix = new byte[] {
            0x30, 0x43, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
            0x71, 0x03, 0x3a, 0x00
        };
        byte[] publicKeyBytes = new byte[] {
            0x5f, (byte) 0xd7, 0x44, (byte) 0x9b, 0x59, (byte) 0xb4, 0x61, (byte) 0xfd,
            0x2c, (byte) 0xe7, (byte) 0x87, (byte) 0xec, 0x61, 0x6a, (byte) 0xd4, 0x6a,
            0x1d, (byte) 0xa1, 0x34, 0x24, (byte) 0x85, (byte) 0xa7, 0x0e, 0x1f,
            (byte) 0x8a, 0x0e, (byte) 0xa7, 0x5d, (byte) 0x80, (byte) 0xe9, 0x67, 0x78,
            (byte) 0xed, (byte) 0xf1, 0x24, 0x76, (byte) 0x9b, 0x46, (byte) 0xc7, 0x06,
            0x1b, (byte) 0xd6, 0x78, 0x3d, (byte) 0xf1, (byte) 0xe5, 0x0f, 0x6c,
            (byte) 0xd1, (byte) 0xfa, 0x1a, (byte) 0xbe, (byte) 0xaf, (byte) 0xe8, 0x25,
            0x61, (byte) 0x80
        };
        return concatenate(prefix, publicKeyBytes);
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

    private static byte[] serializeCapturingEncodedPayload(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PayloadCapturingObjectOutputStream objectOutput =
                new PayloadCapturingObjectOutputStream(output, publicKey.getEncoded())) {
            objectOutput.writeObject(publicKey);
            assertTrue(objectOutput.sawExpectedPayload());
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

    private static Object deserializeUnshared(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return objectInput.readUnshared();
        }
    }

    private static final class PayloadCapturingObjectOutputStream extends ObjectOutputStream {
        private final byte[] expectedPayload;
        private boolean sawExpectedPayload;

        PayloadCapturingObjectOutputStream(
                ByteArrayOutputStream output, byte[] expectedPayload) throws Exception {
            super(output);
            this.expectedPayload = expectedPayload;
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
            this.expectedPayload = expectedPayload;
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
