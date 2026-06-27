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
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.crypto.asymmetric.ECDomainParameters;
import org.bouncycastle.crypto.asymmetric.GOST3410Parameters;
import org.bouncycastle.jcajce.interfaces.ECGOST3410PrivateKey;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.ECDomainParameterSpec;
import org.bouncycastle.jcajce.spec.ECGOST3410PrivateKeySpec;
import org.bouncycastle.jcajce.spec.GOST3410ParameterSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvECGOST3410PrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvECGOST3410PrivateKey";
    private static final BigInteger PRIVATE_VALUE = new BigInteger(
            "1234567890ABCDEF1234567890ABCDEF", 16);
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
    void serializationRoundTripRestoresEcGost3410PrivateKeyFromKeySpec() throws Exception {
        PrivateKey privateKey = ecGost3410KeyFactory().generatePrivate(
                new ECGOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void serializationRoundTripRestoresEcGost3410PrivateKeyFromPkcs8Encoding()
            throws Exception {
        PrivateKey privateKey = ecGost3410KeyFactory().generatePrivate(
                new ECGOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));
        PrivateKey pkcs8PrivateKey = ecGost3410KeyFactory().generatePrivate(
                new PKCS8EncodedKeySpec(privateKey.getEncoded()));

        assertSerializationRoundTrip(pkcs8PrivateKey);
    }

    @Test
    void serializationRoundTripRestoresNestedEcGost3410PrivateKey() throws Exception {
        PrivateKey privateKey = ecGost3410KeyFactory().generatePrivate(
                new ECGOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));

        PrivateKeyHolder holder = assertInstanceOf(
                PrivateKeyHolder.class,
                deserialize(serialize(new PrivateKeyHolder(privateKey))));

        assertDeserializedKey(privateKey, holder.privateKey());
    }

    @Test
    void objectSerializationWritesEcGost3410PrivateKeyClassDescriptor() throws Exception {
        PrivateKey privateKey = ecGost3410KeyFactory().generatePrivate(
                new ECGOST3410PrivateKeySpec(PRIVATE_VALUE, GOST_PARAMETERS));

        byte[] serializedPrivateKey = serialize(privateKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());
        assertTrue(
                new String(serializedPrivateKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PRIVATE_KEY_CLASS_NAME));
    }

    private static void assertSerializationRoundTrip(PrivateKey privateKey) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        assertDeserializedKey(privateKey, deserialize(serialize(privateKey)));
        assertDeserializedKey(privateKey, deserializeUnshared(serializeUnshared(privateKey)));
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        ECGOST3410PrivateKey originalEcGostPrivateKey = assertInstanceOf(
                ECGOST3410PrivateKey.class, privateKey);
        ECGOST3410PrivateKey ecGostPrivateKey = assertInstanceOf(
                ECGOST3410PrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), deserializedKey.getAlgorithm());
        assertEquals(originalEcGostPrivateKey.getS(), ecGostPrivateKey.getS());
        assertEquals(originalEcGostPrivateKey.getParams(), ecGostPrivateKey.getParams());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
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

    private static KeyFactory ecGost3410KeyFactory() throws Exception {
        return KeyFactory.getInstance("ECGOST3410", bouncyCastleFipsProvider());
    }

    private record PrivateKeyHolder(PrivateKey privateKey) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
