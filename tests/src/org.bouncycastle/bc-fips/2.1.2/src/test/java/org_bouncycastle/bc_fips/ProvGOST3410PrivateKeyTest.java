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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.crypto.Algorithm;
import org.bouncycastle.crypto.asymmetric.GOST3410DomainParameters;
import org.bouncycastle.crypto.asymmetric.GOST3410Parameters;
import org.bouncycastle.jcajce.interfaces.GOST3410PrivateKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.GOST3410DomainParameterSpec;
import org.bouncycastle.jcajce.spec.GOST3410ParameterSpec;
import org.bouncycastle.jcajce.spec.GOST3410PrivateKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvGOST3410PrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvGOST3410PrivateKey";
    private static final BigInteger PRIVATE_VALUE = new BigInteger(
            "1234567890ABCDEF1234567890ABCDEF", 16);
    private static final GOST3410ParameterSpec<GOST3410DomainParameterSpec> GOST_PARAMETERS =
            new GOST3410ParameterSpec<>(
                    new GOST3410Parameters<GOST3410DomainParameters>(
                            CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A,
                            CryptoProObjectIdentifiers.gostR3411));

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresGost3410PrivateKeyFromKeySpec() throws Exception {
        PrivateKey privateKey = gost3410KeyFactory().generatePrivate(
                new GOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void serializationRoundTripRestoresGost3410PrivateKeyFromPkcs8Encoding()
            throws Exception {
        PrivateKey privateKey = gost3410KeyFactory().generatePrivate(
                new GOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));
        PrivateKey pkcs8PrivateKey = gost3410KeyFactory().generatePrivate(
                new PKCS8EncodedKeySpec(privateKey.getEncoded()));

        assertSerializationRoundTrip(pkcs8PrivateKey);
    }

    @Test
    void objectSerializationWritesGost3410PrivateKeyClassDescriptor() throws Exception {
        PrivateKey privateKey = gost3410KeyFactory().generatePrivate(
                new GOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));

        byte[] serializedPrivateKey = serialize(privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    @Test
    void serializationRoundTripRestoresNestedGost3410PrivateKey() throws Exception {
        PrivateKey privateKey = gost3410KeyFactory().generatePrivate(
                new GOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));

        PrivateKeyHolder holder = assertInstanceOf(
                PrivateKeyHolder.class,
                deserialize(serialize(new PrivateKeyHolder(privateKey))));

        assertDeserializedKey(privateKey, holder.privateKey());
    }

    @Test
    void customSerializationStreamsObserveGost3410PrivateKeyPayloads() throws Exception {
        PrivateKey privateKey = gost3410KeyFactory().generatePrivate(
                new GOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));
        byte[] encodedPrivateKey = privateKey.getEncoded();

        CapturingObjectOutputStream outputStream = new CapturingObjectOutputStream();
        outputStream.writeObject(privateKey);
        outputStream.close();

        assertTrue(outputStream.sawAlgorithmPayload());
        assertArrayEquals(encodedPrivateKey, outputStream.encodedKeyPayload());

        PayloadResolvingObjectInputStream inputStream = new PayloadResolvingObjectInputStream(
                outputStream.toByteArray(), privateKey.getClass());
        Object deserializedValue = inputStream.readObject();
        inputStream.close();

        assertTrue(inputStream.sawAlgorithmPayload());
        assertArrayEquals(encodedPrivateKey, inputStream.encodedKeyPayload());
        assertDeserializedKey(privateKey, deserializedValue);
    }

    private static void assertSerializationRoundTrip(PrivateKey privateKey) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        assertDeserializedKey(privateKey, deserialize(serialize(privateKey)));
        assertDeserializedKey(privateKey, deserializeUnshared(serializeUnshared(privateKey)));
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        GOST3410PrivateKey originalGostPrivateKey = assertInstanceOf(
                GOST3410PrivateKey.class, privateKey);
        GOST3410PrivateKey gostPrivateKey = assertInstanceOf(
                GOST3410PrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertEquals(originalGostPrivateKey.getX(), gostPrivateKey.getX());
        assertEquals(originalGostPrivateKey.getParams(), gostPrivateKey.getParams());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
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
        try (ObjectInputStream objectInput =
                new PrivateKeyObjectInputStream(input, null)) {
            return objectInput.readObject();
        }
    }

    private static Object deserializeUnshared(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new PrivateKeyObjectInputStream(input, null)) {
            return objectInput.readUnshared();
        }
    }

    private static class PrivateKeyObjectInputStream extends ObjectInputStream {
        private final Class<?> privateKeyClass;

        PrivateKeyObjectInputStream(
                ByteArrayInputStream input, Class<?> privateKeyClass) throws IOException {
            super(input);
            this.privateKeyClass = privateKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PRIVATE_KEY_CLASS_NAME.equals(descriptor.getName())
                    && privateKeyClass != null) {
                return privateKeyClass;
            }
            return super.resolveClass(descriptor);
        }
    }

    private static final class CapturingObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream output;
        private boolean sawAlgorithmPayload;
        private byte[] encodedKeyPayload;

        CapturingObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private CapturingObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
            this.output = output;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof Algorithm algorithm
                    && "GOST3410".equals(algorithm.getName())) {
                sawAlgorithmPayload = true;
            } else if (object instanceof byte[] bytes
                    && encodedKeyPayload == null) {
                encodedKeyPayload = bytes.clone();
            }
            return super.replaceObject(object);
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }

        boolean sawAlgorithmPayload() {
            return sawAlgorithmPayload;
        }

        byte[] encodedKeyPayload() {
            if (encodedKeyPayload == null) {
                return null;
            }
            return encodedKeyPayload.clone();
        }
    }

    private static final class PayloadResolvingObjectInputStream
            extends PrivateKeyObjectInputStream {
        private boolean sawAlgorithmPayload;
        private byte[] encodedKeyPayload;

        PayloadResolvingObjectInputStream(
                byte[] serializedValue, Class<?> privateKeyClass) throws IOException {
            super(new ByteArrayInputStream(serializedValue), privateKeyClass);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof Algorithm algorithm
                    && "GOST3410".equals(algorithm.getName())) {
                sawAlgorithmPayload = true;
            } else if (object instanceof byte[] bytes
                    && encodedKeyPayload == null) {
                encodedKeyPayload = bytes.clone();
            }
            return super.resolveObject(object);
        }

        boolean sawAlgorithmPayload() {
            return sawAlgorithmPayload;
        }

        byte[] encodedKeyPayload() {
            if (encodedKeyPayload == null) {
                return null;
            }
            return encodedKeyPayload.clone();
        }
    }

    private static KeyFactory gost3410KeyFactory() throws Exception {
        return KeyFactory.getInstance("GOST3410", bouncyCastleFipsProvider());
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
