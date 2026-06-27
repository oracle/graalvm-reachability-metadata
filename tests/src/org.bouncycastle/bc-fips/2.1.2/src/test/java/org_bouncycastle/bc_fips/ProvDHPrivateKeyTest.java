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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPrivateKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvDHPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvDHPrivateKey";
    private static final BigInteger MODP_2048_PRIME = new BigInteger("""
            FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1
            29024E088A67CC74020BBEA63B139B22514A08798E3404DD
            EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245
            E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED
            EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D
            C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F
            83655D23DCA3AD961C62F356208552BB9ED529077096966D
            670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B
            E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9
            DE2BCBF6955817183995497CEA956AE515D2261898FA0510
            15728E5A8AACAA68FFFFFFFFFFFFFFFF
            """.replaceAll("\\s", ""), 16);
    private static final BigInteger GENERATOR = BigInteger.TWO;
    private static final BigInteger PRIVATE_EXPONENT = new BigInteger(
            "1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF", 16);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresDhPrivateKeyFromKeySpec() throws Exception {
        DHPrivateKeySpec keySpec = new DHPrivateKeySpec(
                PRIVATE_EXPONENT, MODP_2048_PRIME, GENERATOR);
        PrivateKey privateKey = dhKeyFactory().generatePrivate(keySpec);

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void serializationRoundTripRestoresDhPrivateKeyFromPkcs8Encoding() throws Exception {
        DHPrivateKeySpec keySpec = new DHPrivateKeySpec(
                PRIVATE_EXPONENT, MODP_2048_PRIME, GENERATOR);
        PrivateKey privateKey = dhKeyFactory().generatePrivate(keySpec);
        PrivateKey pkcs8PrivateKey = dhKeyFactory().generatePrivate(
                new PKCS8EncodedKeySpec(privateKey.getEncoded()));

        assertSerializationRoundTrip(pkcs8PrivateKey);
    }

    @Test
    void serializationRoundTripRestoresGeneratedDhPrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "DH", bouncyCastleFipsProvider());
        keyPairGenerator.initialize(new DHParameterSpec(MODP_2048_PRIME, GENERATOR));

        assertSerializationRoundTrip(keyPairGenerator.generateKeyPair().getPrivate());
    }

    @Test
    void serializationRoundTripRestoresDhPrivateKeyWhenNestedInSerializableHolder()
            throws Exception {
        DHPrivateKeySpec keySpec = new DHPrivateKeySpec(
                PRIVATE_EXPONENT, MODP_2048_PRIME, GENERATOR);
        PrivateKey privateKey = dhKeyFactory().generatePrivate(keySpec);

        PrivateKeyHolder deserializedHolder = assertInstanceOf(
                PrivateKeyHolder.class,
                deserialize(serialize(new PrivateKeyHolder(privateKey)), privateKey.getClass()));

        assertDeserializedKey(privateKey, deserializedHolder.privateKey());
    }

    private static void assertSerializationRoundTrip(PrivateKey privateKey) throws Exception {
        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        assertDeserializedKey(
                privateKey, deserialize(serialize(privateKey), privateKey.getClass()));
        assertDeserializedKey(
                privateKey,
                deserializeUnshared(serializeUnshared(privateKey), privateKey.getClass()));
    }

    private static void assertDeserializedKey(
            PrivateKey privateKey, Object deserializedValue) {
        PrivateKey deserializedKey = assertInstanceOf(PrivateKey.class, deserializedValue);
        DHPrivateKey originalDhPrivateKey = assertInstanceOf(DHPrivateKey.class, privateKey);
        DHPrivateKey dhPrivateKey = assertInstanceOf(DHPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("DH", deserializedKey.getAlgorithm());
        assertEquals(originalDhPrivateKey.getX(), dhPrivateKey.getX());
        assertEquals(MODP_2048_PRIME, dhPrivateKey.getParams().getP());
        assertEquals(GENERATOR, dhPrivateKey.getParams().getG());
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

    private static Object deserialize(
            byte[] serializedValue, Class<?> privateKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new PrivateKeyObjectInputStream(input, privateKeyClass)) {
            return objectInput.readObject();
        }
    }

    private static Object deserializeUnshared(
            byte[] serializedValue, Class<?> privateKeyClass) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedValue);
        try (ObjectInputStream objectInput =
                new PrivateKeyObjectInputStream(input, privateKeyClass)) {
            return objectInput.readUnshared();
        }
    }

    private static final class PrivateKeyObjectInputStream extends ObjectInputStream {
        private final Class<?> privateKeyClass;

        PrivateKeyObjectInputStream(
                ByteArrayInputStream input, Class<?> privateKeyClass) throws Exception {
            super(input);
            this.privateKeyClass = privateKeyClass;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (PROVIDER_PRIVATE_KEY_CLASS_NAME.equals(descriptor.getName())) {
                return privateKeyClass;
            }
            if (PrivateKeyHolder.class.getName().equals(descriptor.getName())) {
                return PrivateKeyHolder.class;
            }
            return super.resolveClass(descriptor);
        }
    }

    private static KeyFactory dhKeyFactory() throws Exception {
        return KeyFactory.getInstance("DH", bouncyCastleFipsProvider());
    }

    private record PrivateKeyHolder(PrivateKey privateKey) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return new BouncyCastleFipsProvider();
    }
}
