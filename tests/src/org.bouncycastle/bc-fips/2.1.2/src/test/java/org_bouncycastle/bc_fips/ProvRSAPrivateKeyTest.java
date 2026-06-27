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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvRSAPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvRSAPrivateKey";
    private static final BigInteger MODULUS = BigInteger.valueOf(3233L);
    private static final BigInteger PRIVATE_EXPONENT = BigInteger.valueOf(2753L);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresRsaPrivateKey() throws Exception {
        PrivateKey privateKey = newRsaPrivateKey();

        byte[] serializedPrivateKey = serialize(privateKey);
        Object deserializedValue = deserialize(serializedPrivateKey, privateKey.getClass());
        assertDeserializedKey(privateKey, deserializedValue);
    }

    @Test
    void serializationHooksRestoreRsaPrivateKeyPayload() throws Throwable {
        PrivateKey privateKey = newRsaPrivateKey();
        PrivateKey targetKey = differentRsaPrivateKey();
        assertNotEquals(
                assertInstanceOf(RSAPrivateKey.class, privateKey).getModulus(),
                assertInstanceOf(RSAPrivateKey.class, targetKey).getModulus());

        byte[] hookPayload = writeUsingSerializationHook(privateKey);
        readUsingSerializationHook(targetKey, hookPayload);

        assertDeserializedKey(privateKey, targetKey);
    }

    @Test
    void serializationHooksCallObjectStreamReadAndWriteForPayloadObjects() throws Throwable {
        PrivateKey privateKey = newRsaPrivateKey();
        PrivateKey targetKey = differentRsaPrivateKey();
        HookObjectOutputStream objectOutput = new HookObjectOutputStream();

        serializationHook(privateKey.getClass(), "writeObject", ObjectOutputStream.class)
                .invoke(privateKey, objectOutput);
        assertEquals(2, objectOutput.objects.size());
        assertArrayEquals(privateKey.getEncoded(), (byte[])objectOutput.objects.get(1));

        HookObjectInputStream objectInput = new HookObjectInputStream(objectOutput.objects);
        serializationHook(targetKey.getClass(), "readObject", ObjectInputStream.class)
                .invoke(targetKey, objectInput);

        assertDeserializedKey(privateKey, targetKey);
    }

    @Test
    void objectSerializationObservesRsaPrivateKeyPayloadObjects() throws Exception {
        PrivateKey privateKey = newRsaPrivateKey();
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

        assertSerializedClassDescriptor(output.toByteArray());
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertTrue(objectInput.observedByteArray(expectedEncoding));
        assertDeserializedKey(privateKey, restoredPrivateKey);
    }

    @Test
    void keyFactoryCreatesNonCrtRsaPrivateKeyImplementation() throws Exception {
        PrivateKey privateKey = newRsaPrivateKey();

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertEquals("RSA", privateKey.getAlgorithm());
        assertEquals("PKCS#8", privateKey.getFormat());
    }

    private static void assertSerializedClassDescriptor(byte[] serializedPrivateKey) {
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    private static PrivateKey newRsaPrivateKey() throws Exception {
        RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(MODULUS, PRIVATE_EXPONENT);
        return rsaKeyFactory().generatePrivate(keySpec);
    }

    private static PrivateKey differentRsaPrivateKey() throws Exception {
        RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(
                MODULUS.add(BigInteger.TWO), BigInteger.valueOf(13L));
        return rsaKeyFactory().generatePrivate(keySpec);
    }

    private static void assertDeserializedKey(PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        RSAPrivateKey originalRsaPrivateKey = assertInstanceOf(RSAPrivateKey.class, privateKey);
        RSAPrivateKey rsaPrivateKey = assertInstanceOf(RSAPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("RSA", deserializedKey.getAlgorithm());
        assertEquals(originalRsaPrivateKey.getModulus(), rsaPrivateKey.getModulus());
        assertEquals(
                originalRsaPrivateKey.getPrivateExponent(),
                rsaPrivateKey.getPrivateExponent());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] writeUsingSerializationHook(PrivateKey privateKey) throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new NoDefaultWriteObjectOutputStream(output)) {
            serializationHook(privateKey.getClass(), "writeObject", ObjectOutputStream.class)
                    .invoke(privateKey, objectOutput);
        }
        return output.toByteArray();
    }

    private static void readUsingSerializationHook(PrivateKey privateKey, byte[] hookPayload)
            throws Throwable {
        ByteArrayInputStream input = new ByteArrayInputStream(hookPayload);
        try (ObjectInputStream objectInput = new NoDefaultReadObjectInputStream(input)) {
            serializationHook(privateKey.getClass(), "readObject", ObjectInputStream.class)
                    .invoke(privateKey, objectInput);
        }
    }

    private static MethodHandle serializationHook(
            Class<?> keyClass, String methodName, Class<?> parameterType) throws Exception {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(
                keyClass, MethodHandles.lookup());
        return privateLookup.findSpecial(
                keyClass, methodName, MethodType.methodType(void.class, parameterType), keyClass);
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

    private static final class HookObjectOutputStream extends ObjectOutputStream {
        private final List<Object> objects = new ArrayList<>();

        HookObjectOutputStream() throws IOException {
            super();
        }

        @Override
        public void defaultWriteObject() {
            // `ProvRSAPrivateKey` stores its state through explicit `writeObject` calls.
        }

        @Override
        protected void writeObjectOverride(Object object) {
            objects.add(object);
        }
    }

    private static final class HookObjectInputStream extends ObjectInputStream {
        private final List<Object> objects;
        private int index;

        HookObjectInputStream(List<Object> objects) throws IOException {
            super();
            this.objects = objects;
        }

        @Override
        public void defaultReadObject() {
            // `ProvRSAPrivateKey` restores its state through explicit `readObject` calls.
        }

        @Override
        protected Object readObjectOverride() {
            return objects.get(index++);
        }
    }

    private static final class NoDefaultWriteObjectOutputStream extends ObjectOutputStream {
        NoDefaultWriteObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
        }

        @Override
        public void defaultWriteObject() {
            // `ProvRSAPrivateKey` stores its state through explicit `writeObject` calls.
        }
    }

    private static final class NoDefaultReadObjectInputStream extends ObjectInputStream {
        NoDefaultReadObjectInputStream(ByteArrayInputStream input) throws IOException {
            super(input);
        }

        @Override
        public void defaultReadObject() {
            // `ProvRSAPrivateKey` restores its state through explicit `readObject` calls.
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
        return new BouncyCastleFipsProvider();
    }
}
