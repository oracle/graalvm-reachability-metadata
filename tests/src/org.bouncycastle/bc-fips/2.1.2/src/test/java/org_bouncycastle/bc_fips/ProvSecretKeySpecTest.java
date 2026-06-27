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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.Algorithm;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvSecretKeySpecTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void generatedSecretKeySerializesAndDeserializesWithRawKeyMaterial() throws Exception {
        byte[] encodedKey = new byte[] {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b,
            0x0c, 0x0d, 0x0e, 0x0f
        };
        SecretKey secretKey = generatedAesSecretKey(encodedKey);

        assertProviderSecretKeySpec(secretKey);

        byte[] serializedKey = serialize(secretKey);
        SecretKey deserializedKey = assertInstanceOf(SecretKey.class, deserialize(serializedKey));

        assertEquals(secretKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertEquals("RAW", deserializedKey.getFormat());
        assertArrayEquals(encodedKey, deserializedKey.getEncoded());
    }

    @Test
    void serializationHooksWriteAndReadAlgorithmAndEncodedKey() throws Throwable {
        byte[] encodedKey = new byte[] {
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1a, 0x1b,
            0x1c, 0x1d, 0x1e, 0x1f
        };
        SecretKey secretKey = generatedAesSecretKey(encodedKey);
        SecretKey targetKey = generatedAesSecretKey(new byte[] {
            0x20, 0x21, 0x22, 0x23,
            0x24, 0x25, 0x26, 0x27,
            0x28, 0x29, 0x2a, 0x2b,
            0x2c, 0x2d, 0x2e, 0x2f
        });
        assertProviderSecretKeySpec(secretKey);
        assertProviderSecretKeySpec(targetKey);

        HookObjectOutputStream objectOutput = new HookObjectOutputStream();
        serializationHook(secretKey.getClass(), "writeObject", ObjectOutputStream.class)
                .invoke(secretKey, objectOutput);

        assertEquals(2, objectOutput.objects.size());
        Algorithm algorithm = assertInstanceOf(Algorithm.class, objectOutput.objects.get(0));
        assertEquals("AES", algorithm.getName());
        assertArrayEquals(encodedKey, (byte[])objectOutput.objects.get(1));

        HookObjectInputStream objectInput = new HookObjectInputStream(objectOutput.objects);
        serializationHook(targetKey.getClass(), "readObject", ObjectInputStream.class)
                .invoke(targetKey, objectInput);

        assertArrayEquals(encodedKey, targetKey.getEncoded());
        assertEquals(secretKey.getAlgorithm(), targetKey.getAlgorithm());
    }

    private static void assertProviderSecretKeySpec(SecretKey secretKey) {
        assertEquals(
                "org.bouncycastle.jcajce.provider.ProvSecretKeySpec",
                secretKey.getClass().getName());
    }

    private static SecretKey generatedAesSecretKey(byte[] encodedKey) throws Exception {
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(
                "AES", bouncyCastleFipsProvider());
        return keyFactory.generateSecret(new SecretKeySpec(encodedKey, "AES"));
    }

    private static MethodHandle serializationHook(
            Class<?> keyClass, String methodName, Class<?> parameterType) throws Exception {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(
                keyClass, MethodHandles.lookup());
        return privateLookup.findSpecial(
                keyClass, methodName, MethodType.methodType(void.class, parameterType), keyClass);
    }

    private static byte[] serialize(SecretKey secretKey) throws Exception {
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(outputBuffer)) {
            output.writeObject(secretKey);
        }
        return outputBuffer.toByteArray();
    }

    private static Object deserialize(byte[] serializedKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedKey))) {
            return input.readObject();
        }
    }

    private static final class HookObjectOutputStream extends ObjectOutputStream {
        private final List<Object> objects = new ArrayList<>();

        HookObjectOutputStream() throws Exception {
            super();
        }

        @Override
        public void defaultWriteObject() {
            // `ProvSecretKeySpec` writes its key payload through explicit object writes.
        }

        @Override
        protected void writeObjectOverride(Object object) {
            objects.add(object);
        }
    }

    private static final class HookObjectInputStream extends ObjectInputStream {
        private final List<Object> objects;
        private int index;

        HookObjectInputStream(List<Object> objects) throws Exception {
            super();
            this.objects = objects;
        }

        @Override
        public void defaultReadObject() {
            // `ProvSecretKeySpec` restores its key payload through explicit object reads.
        }

        @Override
        protected Object readObjectOverride() {
            return objects.get(index++);
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
