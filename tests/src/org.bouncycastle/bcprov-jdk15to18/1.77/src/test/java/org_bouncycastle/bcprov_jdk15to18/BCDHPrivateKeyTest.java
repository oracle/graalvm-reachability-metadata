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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.pkcs.DHParameter;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jcajce.provider.asymmetric.dh.BCDHPrivateKey;
import org.junit.jupiter.api.Test;

public class BCDHPrivateKeyTest {
    private static final String BCDH_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.dh.BCDHPrivateKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(7L);

    @Test
    void javaSerializationPreservesDiffieHellmanPrivateKeyParameters() throws Exception {
        BCDHPrivateKey privateKey = generatePrivateKey();

        byte[] serialized = serialize(privateKey);
        BCDHPrivateKey restored = deserialize(serialized);

        assertEquals(BCDH_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCDH_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getX(), restored.getX());
        assertEquals(privateKey.getParams().getP(), restored.getParams().getP());
        assertEquals(privateKey.getParams().getG(), restored.getParams().getG());
        assertEquals(privateKey.getParams().getL(), restored.getParams().getL());
    }

    private static BCDHPrivateKey generatePrivateKey() throws Exception {
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(
            new AlgorithmIdentifier(
                PKCSObjectIdentifiers.dhKeyAgreement,
                new DHParameter(P, G, 4).toASN1Primitive()),
            new ASN1Integer(X));
        return new BCDHPrivateKey(privateKeyInfo);
    }

    private static byte[] serialize(BCDHPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCDHPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new BCDHPrivateKeyObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCDHPrivateKey)objectInputStream.readObject();
        }
    }

    private static final class BCDHPrivateKeyObjectInputStream extends ObjectInputStream {
        private BCDHPrivateKeyObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (BCDH_PRIVATE_KEY_CLASS.equals(descriptor.getName())) {
                return BCDHPrivateKey.class;
            }
            return super.resolveClass(descriptor);
        }
    }
}
