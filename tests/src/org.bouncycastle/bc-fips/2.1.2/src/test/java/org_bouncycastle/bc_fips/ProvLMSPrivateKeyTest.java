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
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.crypto.Algorithm;
import org.bouncycastle.crypto.fips.FipsLMS;
import org.bouncycastle.jcajce.interfaces.LMSPrivateKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.LMOtsParameters;
import org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec;
import org.bouncycastle.jcajce.spec.LMSigParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvLMSPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvLMSPrivateKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresGeneratedLmsPrivateKey() throws Exception {
        PrivateKey privateKey = newLmsPrivateKey();

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void serializationRoundTripRestoresLmsPrivateKeyFromPkcs8Encoding() throws Exception {
        PrivateKey privateKey = newLmsPrivateKey();
        PrivateKey pkcs8PrivateKey = lmsKeyFactory().generatePrivate(
                new PKCS8EncodedKeySpec(privateKey.getEncoded()));

        assertSerializationRoundTrip(pkcs8PrivateKey);
    }

    @Test
    void objectSerializationRestoresLmsPrivateKeyWithObservablePayload() throws Exception {
        PrivateKey privateKey = newLmsPrivateKey();

        PrivateKey deserializedKey = deserializePrivateKey(
                privateKey.getClass(),
                privateKey.getEncoded(),
                serializeWithReplacingPayload(privateKey));

        assertDeserializedKey(privateKey, deserializedKey);
    }

    private static void assertSerializationRoundTrip(PrivateKey privateKey) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        PrivateKey deserializedKey = deserializePrivateKey(
                privateKey.getClass(),
                privateKey.getEncoded(),
                serializeWithReplacingPayload(privateKey));

        assertDeserializedKey(privateKey, deserializedKey);
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey, PrivateKey deserializedKey) {
        LMSPrivateKey originalLmsPrivateKey = assertInstanceOf(
                LMSPrivateKey.class, privateKey);
        LMSPrivateKey lmsPrivateKey = assertInstanceOf(
                LMSPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("LMS", deserializedKey.getAlgorithm());
        assertEquals("PKCS#8", deserializedKey.getFormat());
        assertEquals(originalLmsPrivateKey.getIndex(), lmsPrivateKey.getIndex());
        assertEquals(
                originalLmsPrivateKey.getUsagesRemaining(),
                lmsPrivateKey.getUsagesRemaining());
        assertEquals(originalLmsPrivateKey.getLevels(), lmsPrivateKey.getLevels());
        assertArrayEquals(
                originalLmsPrivateKey.getPublicKey().getEncoded(),
                lmsPrivateKey.getPublicKey().getEncoded());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serializeWithReplacingPayload(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ReplacingKeyEncodingObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static PrivateKey deserializePrivateKey(
            Class<?> privateKeyClass, byte[] keyEncoding, byte[] serializedValue)
            throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ResolvingKeyEncodingObjectInputStream(
                input, privateKeyClass, keyEncoding)) {
            return assertInstanceOf(PrivateKey.class, objectInput.readObject());
        }
    }


    private static final class ReplacingKeyEncodingObjectOutputStream
            extends ObjectOutputStream {
        private boolean keyEncodingReplaced;

        ReplacingKeyEncodingObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        public void defaultWriteObject() {
            // `ProvLMSPrivateKey` stores its state through explicit `writeObject` calls.
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!keyEncodingReplaced && object instanceof byte[]) {
                keyEncodingReplaced = true;
                return FipsLMS.ALGORITHM;
            }
            return object;
        }
    }

    private static final class ResolvingKeyEncodingObjectInputStream extends ObjectInputStream {
        private final Class<?> privateKeyClass;
        private final byte[] keyEncoding;

        ResolvingKeyEncodingObjectInputStream(
                ByteArrayInputStream input, Class<?> privateKeyClass, byte[] keyEncoding)
                throws IOException {
            super(input);
            this.privateKeyClass = privateKeyClass;
            this.keyEncoding = keyEncoding.clone();
            enableResolveObject(true);
        }

        @Override
        public void defaultReadObject() {
            // `ProvLMSPrivateKey` restores its state through explicit `readObject` calls.
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (descriptor.getName().equals(privateKeyClass.getName())) {
                return privateKeyClass;
            }
            return super.resolveClass(descriptor);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof Algorithm) {
                return keyEncoding.clone();
            }
            return object;
        }
    }

    private static PrivateKey newLmsPrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "LMS", bouncyCastleFipsProvider());
        LMSKeyGenParameterSpec parameters = new LMSKeyGenParameterSpec(
                LMSigParameters.lms_sha256_n24_h5,
                LMOtsParameters.sha256_n24_w8);
        keyPairGenerator.initialize(parameters, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return keyPair.getPrivate();
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
