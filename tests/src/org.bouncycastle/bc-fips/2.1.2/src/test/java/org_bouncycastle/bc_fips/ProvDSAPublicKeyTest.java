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
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvDSAPublicKeyTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String PROVIDER_PUBLIC_KEY_CLASS_NAME =
            "org.bouncycastle.jcajce.provider.ProvDSAPublicKey";
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
    private static final BigInteger Y = G.modPow(X, P);

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void objectSerializationRoundTripRestoresDsaPublicKeyFromKeySpec() throws Exception {
        PublicKey publicKey = dsaKeyFactory().generatePublic(
                new DSAPublicKeySpec(Y, P, Q, G));

        byte[] serializedPublicKey = serialize(publicKey);
        PublicKey deserializedPublicKey = deserialize(serializedPublicKey);

        assertTrue(
                new String(serializedPublicKey, StandardCharsets.ISO_8859_1)
                        .contains(PROVIDER_PUBLIC_KEY_CLASS_NAME));
        assertDeserializedKey(publicKey, deserializedPublicKey);
    }

    @Test
    void objectSerializationRoundTripRestoresDsaPublicKeyFromX509Encoding() throws Exception {
        PublicKey publicKey = dsaKeyFactory().generatePublic(
                new DSAPublicKeySpec(Y, P, Q, G));
        PublicKey x509PublicKey = dsaKeyFactory().generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        PublicKey deserializedPublicKey = deserialize(serialize(x509PublicKey));

        assertDeserializedKey(x509PublicKey, deserializedPublicKey);
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
        DSAPublicKey originalDsaPublicKey = assertInstanceOf(DSAPublicKey.class, publicKey);
        DSAPublicKey dsaPublicKey = assertInstanceOf(DSAPublicKey.class, deserializedKey);
        DSAParams dsaParams = dsaPublicKey.getParams();

        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, publicKey.getClass().getName());
        assertEquals(PROVIDER_PUBLIC_KEY_CLASS_NAME, deserializedKey.getClass().getName());
        assertEquals("DSA", deserializedKey.getAlgorithm());
        assertEquals(originalDsaPublicKey.getY(), dsaPublicKey.getY());
        assertEquals(P, dsaParams.getP());
        assertEquals(Q, dsaParams.getQ());
        assertEquals(G, dsaParams.getG());
        assertArrayEquals(publicKey.getEncoded(), deserializedKey.getEncoded());
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
