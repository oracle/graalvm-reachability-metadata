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
        return TestProviders.bcFipsProvider();
    }
}
