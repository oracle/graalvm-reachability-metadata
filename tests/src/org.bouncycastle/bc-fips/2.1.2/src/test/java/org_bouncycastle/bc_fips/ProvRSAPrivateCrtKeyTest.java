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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvRSAPrivateCrtKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvRSAPrivateCrtKey";
    private static final BigInteger MODULUS = BigInteger.valueOf(3233L);
    private static final BigInteger PUBLIC_EXPONENT = BigInteger.valueOf(17L);
    private static final BigInteger PRIVATE_EXPONENT = BigInteger.valueOf(2753L);
    private static final BigInteger PRIME_P = BigInteger.valueOf(61L);
    private static final BigInteger PRIME_Q = BigInteger.valueOf(53L);
    private static final BigInteger PRIME_EXPONENT_P = BigInteger.valueOf(53L);
    private static final BigInteger PRIME_EXPONENT_Q = BigInteger.valueOf(49L);
    private static final BigInteger CRT_COEFFICIENT = BigInteger.valueOf(38L);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresRsaPrivateCrtKey() throws Exception {
        PrivateKey privateKey = newRsaPrivateCrtKey();
        byte[] serializedPrivateKey = serialize(privateKey);
        assertDeserializedKey(
                privateKey,
                deserialize(serializedPrivateKey, privateKey.getClass()));
    }

    @Test
    void objectSerializationWritesRsaPrivateCrtKeyClassDescriptor() throws Exception {
        PrivateKey privateKey = newRsaPrivateCrtKey();

        byte[] serializedPrivateKey = serialize(privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertSerializedClassDescriptor(serializedPrivateKey);
    }

    @Test
    void objectSerializationObservesRsaPrivateCrtKeyPayloadObjects() throws Exception {
        PrivateKey privateKey = newRsaPrivateCrtKey();
        byte[] expectedEncoding = privateKey.getEncoded();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingObjectOutputStream objectOutput = new CapturingObjectOutputStream(output);
        try (objectOutput) {
            objectOutput.writeObject(privateKey);
        }

        if (!objectOutput.observedByteArray(expectedEncoding)) {
            assertSerializedClassDescriptor(output.toByteArray());
            return;
        }

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        CapturingObjectInputStream objectInput = new CapturingObjectInputStream(
                input, privateKey.getClass());
        PrivateKey restoredPrivateKey;
        try (objectInput) {
            restoredPrivateKey = assertInstanceOf(PrivateKey.class, objectInput.readObject());
        }

        assertTrue(objectInput.observedByteArray(expectedEncoding));
        assertDeserializedKey(privateKey, restoredPrivateKey);
    }

    private static void assertSerializedClassDescriptor(byte[] serializedPrivateKey) {
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    private static PrivateKey newRsaPrivateCrtKey() throws Exception {
        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
                MODULUS,
                PUBLIC_EXPONENT,
                PRIVATE_EXPONENT,
                PRIME_P,
                PRIME_Q,
                PRIME_EXPONENT_P,
                PRIME_EXPONENT_Q,
                CRT_COEFFICIENT);
        return rsaKeyFactory().generatePrivate(keySpec);
    }

    private static void assertDeserializedKey(PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        RSAPrivateCrtKey originalRsaPrivateKey = assertInstanceOf(
                RSAPrivateCrtKey.class, privateKey);
        RSAPrivateCrtKey rsaPrivateKey = assertInstanceOf(
                RSAPrivateCrtKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("RSA", deserializedKey.getAlgorithm());
        assertEquals(originalRsaPrivateKey.getModulus(), rsaPrivateKey.getModulus());
        assertEquals(originalRsaPrivateKey.getPublicExponent(), rsaPrivateKey.getPublicExponent());
        assertEquals(
                originalRsaPrivateKey.getPrivateExponent(),
                rsaPrivateKey.getPrivateExponent());
        assertEquals(originalRsaPrivateKey.getPrimeP(), rsaPrivateKey.getPrimeP());
        assertEquals(originalRsaPrivateKey.getPrimeQ(), rsaPrivateKey.getPrimeQ());
        assertEquals(originalRsaPrivateKey.getPrimeExponentP(), rsaPrivateKey.getPrimeExponentP());
        assertEquals(originalRsaPrivateKey.getPrimeExponentQ(), rsaPrivateKey.getPrimeExponentQ());
        assertEquals(originalRsaPrivateKey.getCrtCoefficient(), rsaPrivateKey.getCrtCoefficient());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(
            byte[] serializedValue, Class<?> providerPrivateKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ResolvingObjectInputStream(
                input, providerPrivateKeyClass)) {
            return objectInput.readObject();
        }
    }

    private static class ResolvingObjectInputStream extends ObjectInputStream {
        private final Class<?> providerPrivateKeyClass;

        ResolvingObjectInputStream(
                ByteArrayInputStream input, Class<?> providerPrivateKeyClass) throws IOException {
            super(input);
            this.providerPrivateKeyClass = providerPrivateKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PRIVATE_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return providerPrivateKeyClass;
            }
            if (descriptor.getName().startsWith("org.bouncycastle.")) {
                return providerPrivateKeyClass.getClassLoader().loadClass(descriptor.getName());
            }
            return super.resolveClass(descriptor);
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

    private static final class CapturingObjectInputStream extends ResolvingObjectInputStream {
        private final List<Object> observedObjects = new ArrayList<>();

        CapturingObjectInputStream(
                ByteArrayInputStream input, Class<?> providerPrivateKeyClass) throws IOException {
            super(input, providerPrivateKeyClass);
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

    private static KeyFactory rsaKeyFactory() throws Exception {
        return KeyFactory.getInstance("RSA", bouncyCastleFipsProvider());
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
