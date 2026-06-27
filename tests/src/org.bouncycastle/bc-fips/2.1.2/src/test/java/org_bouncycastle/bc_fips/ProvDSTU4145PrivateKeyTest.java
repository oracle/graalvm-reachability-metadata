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

import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.jcajce.interfaces.DSTU4145PrivateKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.DSTU4145ParameterSpec;
import org.bouncycastle.jcajce.spec.DSTU4145PrivateKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvDSTU4145PrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvDSTU4145PrivateKey";
    private static final BigInteger PRIVATE_VALUE = new BigInteger(
            "1234567890ABCDEF1234567890ABCDEF", 16);
    private static final DSTU4145ParameterSpec DSTU4145_PARAMETERS = new DSTU4145ParameterSpec(
            DSTU4145NamedCurves.getByOID(DSTU4145NamedCurves.getOIDs()[0]));

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresDstu4145PrivateKeyFromKeySpec() throws Exception {
        PrivateKey privateKey = dstu4145KeyFactory().generatePrivate(
                new DSTU4145PrivateKeySpec(PRIVATE_VALUE, DSTU4145_PARAMETERS));

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void serializationRoundTripRestoresDstu4145PrivateKeyFromPkcs8Encoding() throws Exception {
        PrivateKey privateKey = dstu4145KeyFactory().generatePrivate(
                new DSTU4145PrivateKeySpec(PRIVATE_VALUE, DSTU4145_PARAMETERS));
        PrivateKey pkcs8PrivateKey = dstu4145KeyFactory().generatePrivate(
                new PKCS8EncodedKeySpec(privateKey.getEncoded()));

        assertSerializationRoundTrip(pkcs8PrivateKey);
    }

    @Test
    void serializationRoundTripRestoresDstu4145PrivateKeyWhenNestedInSerializableHolder()
            throws Exception {
        PrivateKey privateKey = dstu4145KeyFactory().generatePrivate(
                new DSTU4145PrivateKeySpec(PRIVATE_VALUE, DSTU4145_PARAMETERS));

        PrivateKeyHolder deserializedHolder = assertInstanceOf(
                PrivateKeyHolder.class,
                deserialize(
                        serialize(new PrivateKeyHolder(privateKey)),
                        privateKey.getClass()));

        assertDeserializedKey(privateKey, deserializedHolder.privateKey());
    }

    @Test
    void objectSerializationWritesDstu4145PrivateKeyClassDescriptor() throws Exception {
        PrivateKey privateKey = dstu4145KeyFactory().generatePrivate(
                new DSTU4145PrivateKeySpec(PRIVATE_VALUE, DSTU4145_PARAMETERS));

        byte[] serializedPrivateKey = serialize(privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    private static void assertSerializationRoundTrip(PrivateKey privateKey) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        assertDeserializedKey(
                privateKey,
                deserialize(serialize(privateKey), privateKey.getClass()));
        assertDeserializedKey(
                privateKey,
                deserializeUnshared(serializeUnshared(privateKey), privateKey.getClass()));
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        DSTU4145PrivateKey originalDstu4145PrivateKey = assertInstanceOf(
                DSTU4145PrivateKey.class, privateKey);
        DSTU4145PrivateKey dstu4145PrivateKey = assertInstanceOf(
                DSTU4145PrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("DSTU4145", deserializedKey.getAlgorithm());
        assertEquals(originalDstu4145PrivateKey.getS(), dstu4145PrivateKey.getS());
        assertEquals(DSTU4145_PARAMETERS, dstu4145PrivateKey.getParams());
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

    private static Object deserialize(
            byte[] serializedValue, Class<?> privateKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new PrivateKeyObjectInputStream(input, privateKeyClass)) {
            return objectInput.readObject();
        }
    }

    private static Object deserializeUnshared(
            byte[] serializedValue, Class<?> privateKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new PrivateKeyObjectInputStream(input, privateKeyClass)) {
            return objectInput.readUnshared();
        }
    }

    private static final class PrivateKeyObjectInputStream extends ObjectInputStream {
        private final Class<?> privateKeyClass;

        PrivateKeyObjectInputStream(
                ByteArrayInputStream input, Class<?> privateKeyClass) throws IOException {
            super(input);
            this.privateKeyClass = privateKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PRIVATE_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return privateKeyClass;
            }
            if (PrivateKeyHolder.class.getName().equals(descriptor.getName())) {
                return PrivateKeyHolder.class;
            }
            return super.resolveClass(descriptor);
        }
    }

    private static KeyFactory dstu4145KeyFactory() throws Exception {
        return KeyFactory.getInstance("DSTU4145", bouncyCastleFipsProvider());
    }

    private record PrivateKeyHolder(PrivateKey privateKey) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
