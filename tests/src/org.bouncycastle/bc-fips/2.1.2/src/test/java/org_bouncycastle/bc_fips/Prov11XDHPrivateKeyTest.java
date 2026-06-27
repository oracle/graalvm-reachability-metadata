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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.XECPrivateKey;
import java.security.spec.NamedParameterSpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Prov11XDHPrivateKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PRIVATE_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.Prov11XDHPrivateKey";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void serializationRoundTripRestoresX25519PrivateKeyEncoding() throws Exception {
        assertSerializationRoundTrip("X25519", NamedParameterSpec.X25519);
    }

    @Test
    void serializationRoundTripRestoresX448PrivateKeyEncoding() throws Exception {
        assertSerializationRoundTrip("X448", NamedParameterSpec.X448);
    }

    private static void assertSerializationRoundTrip(
            String algorithm, NamedParameterSpec expectedParameters) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                algorithm, bouncyCastleFipsProvider());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, privateKey.getClass().getName());

        PrivateKey deserializedKey = assertInstanceOf(
                PrivateKey.class, deserialize(serialize(privateKey), privateKey.getClass()));
        XECPrivateKey xecPrivateKey = assertInstanceOf(XECPrivateKey.class, deserializedKey);

        assertEquals(PROVIDER_PRIVATE_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals(algorithm, deserializedKey.getAlgorithm());
        assertEquals(expectedParameters, xecPrivateKey.getParams());
        assertArrayEquals(privateKey.getEncoded(), deserializedKey.getEncoded());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
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
            return super.resolveClass(descriptor);
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
