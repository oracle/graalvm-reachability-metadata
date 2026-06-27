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
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.crypto.Algorithm;
import org.bouncycastle.crypto.asymmetric.GOST3410DomainParameters;
import org.bouncycastle.crypto.asymmetric.GOST3410Parameters;
import org.bouncycastle.jcajce.interfaces.GOST3410PublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.GOST3410DomainParameterSpec;
import org.bouncycastle.jcajce.spec.GOST3410ParameterSpec;
import org.bouncycastle.jcajce.spec.GOST3410PublicKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvGOST3410PublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvGOST3410PublicKey";
    private static final BigInteger PUBLIC_VALUE = new BigInteger(
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
    void serializationHooksRestoreGost3410PublicKeyFromKeySpec() throws Throwable {
        PublicKey publicKey = gost3410KeyFactory().generatePublic(
                new GOST3410PublicKeySpec(PUBLIC_VALUE, GOST_PARAMETERS));

        assertSerializationHookRoundTrip(publicKey);
    }

    @Test
    void serializationHooksRestoreGost3410PublicKeyFromX509Encoding()
            throws Throwable {
        PublicKey publicKey = gost3410KeyFactory().generatePublic(
                new GOST3410PublicKeySpec(PUBLIC_VALUE, GOST_PARAMETERS));
        PublicKey x509PublicKey = gost3410KeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        assertSerializationHookRoundTrip(x509PublicKey);
    }

    @Test
    void objectSerializationWritesGost3410PublicKeyClassDescriptor() throws Exception {
        PublicKey publicKey = gost3410KeyFactory().generatePublic(
                new GOST3410PublicKeySpec(PUBLIC_VALUE, GOST_PARAMETERS));

        byte[] serializedPublicKey = serialize(publicKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    @Test
    void customSerializationStreamsObserveGost3410PublicKeyPayloads() throws Throwable {
        PublicKey publicKey = gost3410KeyFactory().generatePublic(
                new GOST3410PublicKeySpec(PUBLIC_VALUE, GOST_PARAMETERS));
        byte[] encodedPublicKey = publicKey.getEncoded();

        CapturingObjectOutputStream outputStream = writeUsingSerializationHook(publicKey);

        assertTrue(outputStream.sawAlgorithmPayload());
        assertArrayEquals(encodedPublicKey, outputStream.encodedKeyPayload());

        PublicKey targetPublicKey = gost3410KeyFactory().generatePublic(
                new GOST3410PublicKeySpec(PUBLIC_VALUE.add(BigInteger.ONE), GOST_PARAMETERS));
        PayloadResolvingObjectInputStream inputStream = readUsingSerializationHook(
                targetPublicKey, outputStream.toByteArray());

        assertTrue(inputStream.sawAlgorithmPayload());
        assertArrayEquals(encodedPublicKey, inputStream.encodedKeyPayload());
        assertDeserializedKey(publicKey, targetPublicKey);
    }

    private static void assertSerializationHookRoundTrip(PublicKey publicKey) throws Throwable {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        PublicKey restoredPublicKey = restoreWithSerializationHooks(publicKey);

        assertDeserializedKey(publicKey, restoredPublicKey);
    }

    private static PublicKey restoreWithSerializationHooks(PublicKey publicKey)
            throws Throwable {
        PublicKey targetPublicKey = gost3410KeyFactory().generatePublic(
                new GOST3410PublicKeySpec(PUBLIC_VALUE.add(BigInteger.ONE), GOST_PARAMETERS));
        assertNotEquals(
                assertInstanceOf(GOST3410PublicKey.class, publicKey).getY(),
                assertInstanceOf(GOST3410PublicKey.class, targetPublicKey).getY());

        CapturingObjectOutputStream outputStream = writeUsingSerializationHook(publicKey);
        readUsingSerializationHook(targetPublicKey, outputStream.toByteArray());

        return targetPublicKey;
    }

    private static CapturingObjectOutputStream writeUsingSerializationHook(
            PublicKey publicKey) throws Throwable {
        CapturingObjectOutputStream objectOutput = new CapturingObjectOutputStream();
        serializationHook(publicKey.getClass(), "writeObject", ObjectOutputStream.class)
                .invoke(publicKey, objectOutput);
        objectOutput.close();
        return objectOutput;
    }

    private static PayloadResolvingObjectInputStream readUsingSerializationHook(
            PublicKey publicKey, byte[] hookPayload) throws Throwable {
        PayloadResolvingObjectInputStream objectInput =
                new PayloadResolvingObjectInputStream(hookPayload);
        serializationHook(publicKey.getClass(), "readObject", ObjectInputStream.class)
                .invoke(publicKey, objectInput);
        objectInput.close();
        return objectInput;
    }

    private static MethodHandle serializationHook(
            Class<?> keyClass, String methodName, Class<?> parameterType) throws Exception {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(
                keyClass, MethodHandles.lookup());
        return privateLookup.findSpecial(
                keyClass, methodName, MethodType.methodType(void.class, parameterType), keyClass);
    }

    private static void assertDeserializedKey(
            PublicKey publicKey, Object deserializedValue) {
        PublicKey deserializedKey = assertInstanceOf(PublicKey.class, deserializedValue);
        GOST3410PublicKey originalGostPublicKey = assertInstanceOf(
                GOST3410PublicKey.class, publicKey);
        GOST3410PublicKey gostPublicKey = assertInstanceOf(
                GOST3410PublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertEquals("X.509", deserializedKey.getFormat());
        assertEquals(originalGostPublicKey.getY(), gostPublicKey.getY());
        assertEquals(originalGostPublicKey.getParams(), gostPublicKey.getParams());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
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
        public void defaultWriteObject() {
            // `ProvGOST3410PublicKey` writes all restorable state explicitly.
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

    private static final class PayloadResolvingObjectInputStream extends ObjectInputStream {
        private boolean sawAlgorithmPayload;
        private byte[] encodedKeyPayload;

        PayloadResolvingObjectInputStream(byte[] serializedValue) throws IOException {
            super(new ByteArrayInputStream(serializedValue));
            enableResolveObject(true);
        }

        @Override
        public void defaultReadObject() {
            // `ProvGOST3410PublicKey` restores all state from explicit payloads.
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

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return new BouncyCastleFipsProvider();
    }
}
