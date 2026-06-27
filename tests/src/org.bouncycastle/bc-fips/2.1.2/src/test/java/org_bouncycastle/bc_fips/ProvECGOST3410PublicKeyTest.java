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
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.crypto.asymmetric.ECDomainParameters;
import org.bouncycastle.crypto.asymmetric.GOST3410Parameters;
import org.bouncycastle.jcajce.interfaces.ECGOST3410PublicKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.ECDomainParameterSpec;
import org.bouncycastle.jcajce.spec.ECGOST3410PublicKeySpec;
import org.bouncycastle.jcajce.spec.GOST3410ParameterSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvECGOST3410PublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvECGOST3410PublicKey";
    private static final GOST3410ParameterSpec<ECDomainParameterSpec> GOST_PARAMETERS =
            new GOST3410ParameterSpec<>(
                    new GOST3410Parameters<ECDomainParameters>(
                            CryptoProObjectIdentifiers.gostR3410_2001_CryptoPro_A,
                            CryptoProObjectIdentifiers.gostR3411));

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresEcGost3410PublicKeyFromKeySpec() throws Exception {
        PublicKey publicKey = ecGost3410KeyFactory().generatePublic(
                new ECGOST3410PublicKeySpec(
                        GOST_PARAMETERS.getDomainParametersSpec().getGenerator(),
                        GOST_PARAMETERS));

        assertSerializationRoundTrip(publicKey);
    }

    @Test
    void serializationRoundTripRestoresEcGost3410PublicKeyFromX509Encoding()
            throws Exception {
        PublicKey publicKey = ecGost3410KeyFactory().generatePublic(
                new ECGOST3410PublicKeySpec(
                        GOST_PARAMETERS.getDomainParametersSpec().getGenerator(),
                        GOST_PARAMETERS));
        PublicKey x509PublicKey = ecGost3410KeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        assertSerializationRoundTrip(x509PublicKey);
    }

    @Test
    void serializationRoundTripRestoresGeneratedEcGost3410PublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "ECGOST3410", bouncyCastleFipsProvider());
        keyPairGenerator.initialize(GOST_PARAMETERS);

        assertSerializationRoundTrip(keyPairGenerator.generateKeyPair().getPublic());
    }

    @Test
    void serializationRoundTripRestoresNestedEcGost3410PublicKey() throws Exception {
        PublicKey publicKey = ecGost3410KeyFactory().generatePublic(
                new ECGOST3410PublicKeySpec(
                        GOST_PARAMETERS.getDomainParametersSpec().getGenerator(),
                        GOST_PARAMETERS));

        PublicKeyHolder holder = assertInstanceOf(
                PublicKeyHolder.class,
                deserialize(serialize(new PublicKeyHolder(publicKey))));

        assertDeserializedKey(publicKey, holder.publicKey());
    }

    @Test
    void objectSerializationWritesEcGost3410PublicKeyClassDescriptor() throws Exception {
        PublicKey publicKey = ecGost3410KeyFactory().generatePublic(
                new ECGOST3410PublicKeySpec(
                        GOST_PARAMETERS.getDomainParametersSpec().getGenerator(),
                        GOST_PARAMETERS));

        byte[] serializedPublicKey = serialize(publicKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    @Test
    void serializationRoundTripWithExplicitClassResolverRestoresEcGost3410PublicKey()
            throws Exception {
        PublicKey publicKey = ecGost3410KeyFactory().generatePublic(
                new ECGOST3410PublicKeySpec(
                        GOST_PARAMETERS.getDomainParametersSpec().getGenerator(),
                        GOST_PARAMETERS));

        Object restoredPublicKey = deserializeWithProviderClassResolver(
                serialize(publicKey), publicKey.getClass());

        assertDeserializedKey(publicKey, restoredPublicKey);
    }

    private static void assertSerializationRoundTrip(PublicKey publicKey) throws Exception {
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());

        assertDeserializedKey(publicKey, deserialize(serialize(publicKey)));
        assertDeserializedKey(publicKey, deserializeUnshared(serializeUnshared(publicKey)));
    }

    private static void assertDeserializedKey(
            PublicKey publicKey, Object deserializedValue) {
        PublicKey deserializedKey = assertInstanceOf(PublicKey.class, deserializedValue);
        ECGOST3410PublicKey originalEcGostPublicKey = assertInstanceOf(
                ECGOST3410PublicKey.class, publicKey);
        ECGOST3410PublicKey ecGostPublicKey = assertInstanceOf(
                ECGOST3410PublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertEquals("X.509", deserializedKey.getFormat());
        assertEquals(originalEcGostPublicKey.getW(), ecGostPublicKey.getW());
        assertEquals(originalEcGostPublicKey.getParams(), ecGostPublicKey.getParams());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
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

    private static Object deserialize(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return objectInput.readObject();
        }
    }

    private static Object deserializeUnshared(byte[] serializedValue) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return objectInput.readUnshared();
        }
    }

    private static Object deserializeWithProviderClassResolver(
            byte[] serializedValue, Class<?> providerKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput = new ProviderClassResolvingObjectInputStream(
                input, providerKeyClass)) {
            return objectInput.readObject();
        }
    }

    private static PublicKey generatedEcGost3410PublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "ECGOST3410", bouncyCastleFipsProvider());
        keyPairGenerator.initialize(GOST_PARAMETERS);
        return keyPairGenerator.generateKeyPair().getPublic();
    }

    private static KeyFactory ecGost3410KeyFactory() throws Exception {
        return KeyFactory.getInstance("ECGOST3410", bouncyCastleFipsProvider());
    }

    private record PublicKeyHolder(PublicKey publicKey) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static final class ProviderClassResolvingObjectInputStream
            extends ObjectInputStream {
        private final Class<?> providerKeyClass;

        ProviderClassResolvingObjectInputStream(
                ByteArrayInputStream input, Class<?> providerKeyClass) throws IOException {
            super(input);
            this.providerKeyClass = providerKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PUBLIC_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return providerKeyClass;
            }
            return super.resolveClass(descriptor);
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
