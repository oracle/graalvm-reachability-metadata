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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvDSAPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvDSAPrivateKey";
    private static final BigInteger P = new BigInteger("""
            B10B8F96A080E01DDE92DE5EAE5D54EC52C99FBCFB06A3C69A6A9DCA52D
            23B616073E28675A23D189838EF1E2EE652C013ECB4AEA906112324975C3C
            D49B83BFACCBDD7D90C4BD7098488E9C219A73724EFFD6FAE5644738FAA
            31A4FF55BCCC0A151AF5F0DC8B4BD45BF37DF365C1A65E68CFDA76D4DA
            708DF1FB2BC2E4A4371
            """.replaceAll("\\s", ""), 16);
    private static final BigInteger Q = new BigInteger(
            "F518AA8781A8DF278ABA4E7D64B7CB9D49462353", 16);
    private static final BigInteger G = new BigInteger("""
            A4D1CBD5C3FD34126765A442EFB99905F8104DD258AC507FD6406CFF142
            66D31266FEA1E5C41564B777E690F5504F213160217B4B01B886A5E915
            47F9E2749F4D7FBD7D3B9A92EE1909D0D2263F80A76A6A24C087A091
            F531DBF0A0169B6A28AD662A4D18E73AFA32D779D5918D08BC8858F4
            DCEF97C2A24855E6EEB22B3B2E5
            """.replaceAll("\\s", ""), 16);
    private static final BigInteger X = new BigInteger(
            "65C73E6BE4A07EF9C21F8D586BAE4D438D30C6A3", 16);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void objectSerializationWritesDsaPrivateKeyClassDescriptor() throws Exception {
        PrivateKey privateKey = dsaKeyFactory().generatePrivate(
                new DSAPrivateKeySpec(X, P, Q, G));

        byte[] serializedPrivateKey = serialize(privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    private static void assertSerializationHookRoundTrip(PrivateKey privateKey) throws Throwable {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        PrivateKey restoredPrivateKey = restoreWithSerializationHooks(privateKey);

        assertDeserializedKey(privateKey, restoredPrivateKey);
    }

    private static PrivateKey restoreWithSerializationHooks(PrivateKey privateKey)
            throws Throwable {
        PrivateKey targetKey = dsaKeyFactory().generatePrivate(
                new DSAPrivateKeySpec(X.add(BigInteger.ONE), P, Q, G));
        assertNotEquals(
                assertInstanceOf(DSAPrivateKey.class, privateKey).getX(),
                assertInstanceOf(DSAPrivateKey.class, targetKey).getX());

        byte[] hookPayload = writeUsingSerializationHook(privateKey);
        readUsingSerializationHook(targetKey, hookPayload);

        return targetKey;
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
        return privateLookup.findVirtual(
                keyClass, methodName, MethodType.methodType(void.class, parameterType));
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        DSAPrivateKey originalDsaPrivateKey = assertInstanceOf(DSAPrivateKey.class, privateKey);
        DSAPrivateKey dsaPrivateKey = assertInstanceOf(DSAPrivateKey.class, deserializedKey);
        DSAParams dsaParams = dsaPrivateKey.getParams();

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("DSA", deserializedKey.getAlgorithm());
        assertEquals(originalDsaPrivateKey.getX(), dsaPrivateKey.getX());
        assertEquals(P, dsaParams.getP());
        assertEquals(Q, dsaParams.getQ());
        assertEquals(G, dsaParams.getG());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static final class NoDefaultWriteObjectOutputStream extends ObjectOutputStream {
        NoDefaultWriteObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
        }

        @Override
        public void defaultWriteObject() {
            // `ProvDSAPrivateKey` stores its state through explicit `writeObject` calls.
        }
    }

    private static final class NoDefaultReadObjectInputStream extends ObjectInputStream {
        NoDefaultReadObjectInputStream(ByteArrayInputStream input) throws IOException {
            super(input);
        }

        @Override
        public void defaultReadObject() {
            // `ProvDSAPrivateKey` restores its state through explicit `readObject` calls.
        }
    }

    private static KeyFactory dsaKeyFactory() throws Exception {
        return KeyFactory.getInstance("DSA", bouncyCastleFipsProvider());
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
