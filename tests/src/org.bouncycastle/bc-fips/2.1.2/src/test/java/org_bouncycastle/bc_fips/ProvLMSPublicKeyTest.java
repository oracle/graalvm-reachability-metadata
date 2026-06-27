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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jcajce.interfaces.LMSPublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.LMOtsParameters;
import org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec;
import org.bouncycastle.jcajce.spec.LMSigParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvLMSPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvLMSPublicKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void objectSerializationRoundTripRestoresGeneratedLmsPublicKey() throws Exception {
        PublicKey publicKey = newLmsPublicKey();

        PublicKey deserializedKey = deserialize(serialize(publicKey));

        assertDeserializedKey(publicKey, deserializedKey);
    }

    @Test
    void objectSerializationRoundTripRestoresLmsPublicKeyFromX509Encoding() throws Exception {
        PublicKey publicKey = newLmsPublicKey();
        PublicKey x509PublicKey = lmsKeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        PublicKey deserializedKey = deserialize(serialize(x509PublicKey));

        assertDeserializedKey(x509PublicKey, deserializedKey);
    }

    @Test
    void objectSerializationWritesLmsPublicKeyClassDescriptor() throws Exception {
        PublicKey publicKey = newLmsPublicKey();

        byte[] serializedPublicKey = serialize(publicKey);
        String serializedText = new String(serializedPublicKey, StandardCharsets.ISO_8859_1);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertTrue(serializedText.contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    private static CapturingObjectOutputStream writeUsingSerializationHook(PublicKey publicKey)
            throws Throwable {
        CapturingObjectOutputStream objectOutput = new CapturingObjectOutputStream();
        serializationHook(publicKey.getClass(), "writeObject", ObjectOutputStream.class)
                .invoke(publicKey, objectOutput);
        objectOutput.close();
        return objectOutput;
    }

    private static void readUsingSerializationHook(PublicKey publicKey, byte[] hookPayload)
            throws Throwable {
        try (ObjectInputStream objectInput = new NoDefaultReadObjectInputStream(hookPayload)) {
            serializationHook(publicKey.getClass(), "readObject", ObjectInputStream.class)
                    .invoke(publicKey, objectInput);
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

    private static PublicKey deserialize(byte[] serializedPublicKey) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedPublicKey);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return assertInstanceOf(PublicKey.class, objectInput.readObject());
        }
    }

    private static void assertDeserializedKey(PublicKey publicKey, PublicKey deserializedKey) {
        LMSPublicKey originalLmsPublicKey = assertInstanceOf(LMSPublicKey.class, publicKey);
        LMSPublicKey lmsPublicKey = assertInstanceOf(LMSPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("LMS", deserializedKey.getAlgorithm());
        assertEquals("X.509", deserializedKey.getFormat());
        assertEquals(originalLmsPublicKey.getLevels(), lmsPublicKey.getLevels());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
        assertEquals(publicKey, deserializedKey);
        assertEquals(publicKey.hashCode(), deserializedKey.hashCode());
    }

    private static final class CapturingObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream output;
        private byte[] encodedKeyPayload;

        CapturingObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private CapturingObjectOutputStream(ByteArrayOutputStream output) throws Exception {
            super(output);
            this.output = output;
            enableReplaceObject(true);
        }

        @Override
        public void defaultWriteObject() {
            // `ProvLMSPublicKey` writes all restorable state explicitly.
        }

        @Override
        protected Object replaceObject(Object object) {
            if (object instanceof byte[] bytes && encodedKeyPayload == null) {
                encodedKeyPayload = bytes.clone();
            }
            return object;
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }

        boolean sawEncodedKeyPayload() {
            return encodedKeyPayload != null;
        }

        byte[] encodedKeyPayload() {
            return encodedKeyPayload.clone();
        }
    }

    private static final class NoDefaultReadObjectInputStream extends ObjectInputStream {
        NoDefaultReadObjectInputStream(byte[] hookPayload) throws Exception {
            super(new ByteArrayInputStream(hookPayload));
        }

        @Override
        public void defaultReadObject() {
            // `ProvLMSPublicKey` restores all state from the following encoded payload.
        }
    }

    private static PublicKey newLmsPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "LMS", bouncyCastleFipsProvider());
        LMSKeyGenParameterSpec parameters = new LMSKeyGenParameterSpec(
                LMSigParameters.lms_sha256_n24_h5,
                LMOtsParameters.sha256_n24_w8);
        keyPairGenerator.initialize(parameters, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return keyPair.getPublic();
    }

    private static KeyFactory lmsKeyFactory() throws Exception {
        return KeyFactory.getInstance("LMS", bouncyCastleFipsProvider());
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
