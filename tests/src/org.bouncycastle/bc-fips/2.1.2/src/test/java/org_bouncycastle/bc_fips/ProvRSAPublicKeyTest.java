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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvRSAPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY =
            "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvRSAPublicKey";
    private static final BigInteger MODULUS = BigInteger.valueOf(3233L);
    private static final BigInteger PUBLIC_EXPONENT = BigInteger.valueOf(17L);
    private static final BigInteger PRIVATE_EXPONENT = BigInteger.valueOf(2753L);
    private static final BigInteger PRIME_P = BigInteger.valueOf(61L);
    private static final BigInteger PRIME_Q = BigInteger.valueOf(53L);
    private static final BigInteger PRIME_EXPONENT_P = BigInteger.valueOf(53L);
    private static final BigInteger PRIME_EXPONENT_Q = BigInteger.valueOf(49L);
    private static final BigInteger CRT_COEFFICIENT = BigInteger.valueOf(38L);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void objectSerializationRoundTripRestoresRsaPublicKeyFromKeySpec() throws Exception {
        PublicKey publicKey = newRsaPublicKey();

        byte[] serializedPublicKey = serialize(publicKey);
        PublicKey deserializedPublicKey = deserialize(serializedPublicKey);

        assertSerializedClassDescriptor(serializedPublicKey);
        assertDeserializedKey(publicKey, deserializedPublicKey);
    }

    @Test
    void objectSerializationRoundTripRestoresRsaPublicKeyFromX509Encoding() throws Exception {
        PublicKey publicKey = newRsaPublicKey();
        PublicKey x509PublicKey = rsaKeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        PublicKey deserializedPublicKey = deserialize(serialize(x509PublicKey));

        assertDeserializedKey(x509PublicKey, deserializedPublicKey);
    }

    private static void assertSerializedClassDescriptor(byte[] serializedPublicKey) {
        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
    }

    private static PublicKey newRsaPublicKey() throws Exception {
        PrivateKey rsaPrivateKey = newRsaPrivateCrtKey();
        PublicKey rsaPublicKey = rsaKeyFactory().generatePublic(
                new RSAPublicKeySpec(MODULUS, PUBLIC_EXPONENT));

        assertEquals("RSA", rsaPrivateKey.getAlgorithm());
        return rsaPublicKey;
    }

    private static PrivateKey newRsaPrivateCrtKey() throws Exception {
        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
                MODULUS,
                PUBLIC_EXPONENT,
                PRIVATE_EXPONENT,
                PRIME_P,
                PRIME_Q,
                PRIME_EXPONENT_P,
                PRIME_EXPONENT_Q,
                CRT_COEFFICIENT);
        return rsaKeyFactory().generatePrivate(keySpec);
    }

    private static void assertDeserializedKey(PublicKey publicKey, PublicKey deserializedKey) {
        RSAPublicKey originalRsaPublicKey = assertInstanceOf(RSAPublicKey.class, publicKey);
        RSAPublicKey rsaPublicKey = assertInstanceOf(RSAPublicKey.class, deserializedKey);

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("RSA", deserializedKey.getAlgorithm());
        assertEquals("X.509", deserializedKey.getFormat());
        assertEquals(originalRsaPublicKey.getModulus(), rsaPublicKey.getModulus());
        assertEquals(originalRsaPublicKey.getPublicExponent(), rsaPublicKey.getPublicExponent());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
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

    private static KeyFactory rsaKeyFactory() throws Exception {
        return KeyFactory.getInstance("RSA", bouncyCastleFipsProvider());
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
