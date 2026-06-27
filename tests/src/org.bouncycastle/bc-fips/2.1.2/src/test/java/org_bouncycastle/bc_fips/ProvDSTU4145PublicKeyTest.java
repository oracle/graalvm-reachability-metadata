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
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.jcajce.interfaces.DSTU4145PublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.DSTU4145ParameterSpec;
import org.bouncycastle.jcajce.spec.DSTU4145PublicKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvDSTU4145PublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvDSTU4145PublicKey";
    private static final DSTU4145ParameterSpec DSTU4145_PARAMETERS = new DSTU4145ParameterSpec(
            DSTU4145NamedCurves.getByOID(DSTU4145NamedCurves.getOIDs()[0]));
    private static final DSTU4145ParameterSpec OTHER_DSTU4145_PARAMETERS =
            new DSTU4145ParameterSpec(
                    DSTU4145NamedCurves.getByOID(DSTU4145NamedCurves.getOIDs()[1]));

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationHooksRestoreDstu4145PublicKeyFromKeySpec() throws Throwable {
        PublicKey publicKey = dstu4145KeyFactory().generatePublic(
                new DSTU4145PublicKeySpec(DSTU4145_PARAMETERS.getGenerator(), DSTU4145_PARAMETERS));

        assertSerializationHookRoundTrip(publicKey);
    }

    @Test
    void serializationHooksRestoreDstu4145PublicKeyFromX509Encoding() throws Throwable {
        PublicKey publicKey = dstu4145KeyFactory().generatePublic(
                new DSTU4145PublicKeySpec(DSTU4145_PARAMETERS.getGenerator(), DSTU4145_PARAMETERS));
        PublicKey x509PublicKey = dstu4145KeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        assertSerializationHookRoundTrip(x509PublicKey);
    }

    @Test
    void objectSerializationWritesDstu4145PublicKeyClassDescriptor() throws Exception {
        PublicKey publicKey = dstu4145KeyFactory().generatePublic(
                new DSTU4145PublicKeySpec(DSTU4145_PARAMETERS.getGenerator(), DSTU4145_PARAMETERS));

        byte[] serializedPublicKey = serialize(publicKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    private static void assertSerializationHookRoundTrip(PublicKey publicKey) throws Throwable {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        PublicKey restoredPublicKey = restoreWithSerializationHooks(publicKey);

        assertDeserializedKey(publicKey, restoredPublicKey);
    }

    private static PublicKey restoreWithSerializationHooks(PublicKey publicKey) throws Throwable {
        PublicKey targetKey = dstu4145KeyFactory().generatePublic(
                new DSTU4145PublicKeySpec(
                        OTHER_DSTU4145_PARAMETERS.getGenerator(), OTHER_DSTU4145_PARAMETERS));
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, targetKey.getClass().getName());
        assertNotEquals(
                assertInstanceOf(DSTU4145PublicKey.class, publicKey).getParams(),
                assertInstanceOf(DSTU4145PublicKey.class, targetKey).getParams());

        byte[] hookPayload = writeUsingSerializationHook(publicKey);
        readUsingSerializationHook(targetKey, hookPayload);

        return targetKey;
    }

    private static byte[] writeUsingSerializationHook(PublicKey publicKey) throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new NoDefaultWriteObjectOutputStream(output)) {
            serializationHook(publicKey.getClass(), "writeObject", ObjectOutputStream.class)
                    .invoke(publicKey, objectOutput);
        }
        return output.toByteArray();
    }

    private static void readUsingSerializationHook(PublicKey publicKey, byte[] hookPayload)
            throws Throwable {
        ByteArrayInputStream input = new ByteArrayInputStream(hookPayload);
        try (ObjectInputStream objectInput = new NoDefaultReadObjectInputStream(input)) {
            serializationHook(publicKey.getClass(), "readObject", ObjectInputStream.class)
                    .invoke(publicKey, objectInput);
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
            PublicKey publicKey, Object deserializedValue) {
        PublicKey deserializedKey = assertInstanceOf(PublicKey.class, deserializedValue);
        DSTU4145PublicKey originalDstu4145PublicKey = assertInstanceOf(
                DSTU4145PublicKey.class, publicKey);
        DSTU4145PublicKey dstu4145PublicKey = assertInstanceOf(
                DSTU4145PublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("DSTU4145", deserializedKey.getAlgorithm());
        assertEquals("X.509", deserializedKey.getFormat());
        assertEquals(originalDstu4145PublicKey.getW(), dstu4145PublicKey.getW());
        assertEquals(DSTU4145_PARAMETERS, dstu4145PublicKey.getParams());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static KeyFactory dstu4145KeyFactory() throws Exception {
        return KeyFactory.getInstance("DSTU4145", bouncyCastleFipsProvider());
    }

    private static final class NoDefaultWriteObjectOutputStream extends ObjectOutputStream {
        NoDefaultWriteObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
        }

        @Override
        public void defaultWriteObject() {
            // `ProvDSTU4145PublicKey` stores its state through explicit `writeObject` calls.
        }
    }

    private static final class NoDefaultReadObjectInputStream extends ObjectInputStream {
        NoDefaultReadObjectInputStream(ByteArrayInputStream input) throws IOException {
            super(input);
        }

        @Override
        public void defaultReadObject() {
            // `ProvDSTU4145PublicKey` restores its state through explicit `readObject` calls.
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
