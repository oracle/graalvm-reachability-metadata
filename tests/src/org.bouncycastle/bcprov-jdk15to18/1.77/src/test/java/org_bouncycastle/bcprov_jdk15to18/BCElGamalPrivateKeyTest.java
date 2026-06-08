/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.PrivateKey;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.oiw.ElGamalParameter;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jcajce.provider.asymmetric.elgamal.BCElGamalPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.elgamal.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.ElGamalPrivateKey;
import org.junit.jupiter.api.Test;

public class BCElGamalPrivateKeyTest {
    private static final String BC_ELGAMAL_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.elgamal.BCElGamalPrivateKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(7L);

    @Test
    void javaSerializationWritesElGamalPrivateKeyParameters() throws Exception {
        ElGamalPrivateKey privateKey = generatePrivateKey();

        byte[] serialized = serialize(privateKey);

        assertEquals(BC_ELGAMAL_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals("ElGamal", privateKey.getAlgorithm());
        assertEquals("PKCS#8", privateKey.getFormat());
        assertEquals(X, privateKey.getX());
        assertEquals(P, privateKey.getParameters().getP());
        assertEquals(G, privateKey.getParameters().getG());
        assertEquals(P, privateKey.getParams().getP());
        assertEquals(G, privateKey.getParams().getG());
        assertTrue(serialized.length > 0);
    }

    private static BCElGamalPrivateKey generatePrivateKey() throws Exception {
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(
            new AlgorithmIdentifier(
                OIWObjectIdentifiers.elGamalAlgorithm,
                new ElGamalParameter(P, G).toASN1Primitive()),
            new ASN1Integer(X));
        PrivateKey privateKey = new KeyFactorySpi().generatePrivate(privateKeyInfo);
        return (BCElGamalPrivateKey)privateKey;
    }

    private static byte[] serialize(ElGamalPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }
}
