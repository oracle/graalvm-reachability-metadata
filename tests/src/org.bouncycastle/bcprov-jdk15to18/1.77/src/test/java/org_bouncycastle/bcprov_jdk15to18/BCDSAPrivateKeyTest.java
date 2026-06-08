/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.interfaces.DSAPrivateKey;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.BCDSAPrivateKey;
import org.junit.jupiter.api.Test;

public class BCDSAPrivateKeyTest {
    private static final String BCDSA_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.dsa.BCDSAPrivateKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger X = BigInteger.valueOf(3L);

    @Test
    void javaSerializationPreservesDsaPrivateKeyParameters() throws Exception {
        DSAPrivateKey privateKey = generatePrivateKey();

        byte[] serialized = serialize(privateKey);
        DSAPrivateKey restored = deserialize(serialized);

        assertEquals(BCDSA_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCDSA_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getX(), restored.getX());
        assertEquals(privateKey.getParams().getP(), restored.getParams().getP());
        assertEquals(privateKey.getParams().getQ(), restored.getParams().getQ());
        assertEquals(privateKey.getParams().getG(), restored.getParams().getG());
    }

    private static BCDSAPrivateKey generatePrivateKey() throws Exception {
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(
            new AlgorithmIdentifier(
                X9ObjectIdentifiers.id_dsa,
                new DSAParameter(P, Q, G).toASN1Primitive()),
            new ASN1Integer(X));
        return new BCDSAPrivateKey(privateKeyInfo);
    }

    private static byte[] serialize(DSAPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static DSAPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (DSAPrivateKey)objectInputStream.readObject();
        }
    }
}
