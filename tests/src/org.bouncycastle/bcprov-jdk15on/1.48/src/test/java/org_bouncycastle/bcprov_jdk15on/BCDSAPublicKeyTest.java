/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.math.BigInteger;
import java.security.interfaces.DSAPublicKey;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.BCDSAPublicKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDSAPublicKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger Y = BigInteger.valueOf(4L);

    @Test
    void subjectPublicKeyInfoCreatedPublicKeySerializationRoundTripPreservesDsaParameters() throws Exception {
        DSAPublicKey publicKey = createPublicKey();

        DSAPublicKey restoredKey = (DSAPublicKey) deserialize(serialize(publicKey));

        assertThat(publicKey).isInstanceOf(BCDSAPublicKey.class);
        assertThat(restoredKey).isInstanceOf(BCDSAPublicKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSA");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getY()).isEqualTo(Y);
        assertThat(restoredKey.getParams().getP()).isEqualTo(P);
        assertThat(restoredKey.getParams().getQ()).isEqualTo(Q);
        assertThat(restoredKey.getParams().getG()).isEqualTo(G);
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    private static DSAPublicKey createPublicKey() throws Exception {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
                X9ObjectIdentifiers.id_dsa,
                new DSAParameter(P, Q, G).toASN1Primitive());
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(algorithmIdentifier, new ASN1Integer(Y));

        return new BCDSAPublicKey(publicKeyInfo);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        try (ObjectInputStream objectStream = new BouncyCastleObjectInputStream(
                new ByteArrayInputStream(serializedValue))) {
            return objectStream.readObject();
        }
    }

    private static final class BouncyCastleObjectInputStream extends ObjectInputStream {
        private BouncyCastleObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDescription) throws IOException, ClassNotFoundException {
            if (BCDSAPublicKey.class.getName().equals(classDescription.getName())) {
                return BCDSAPublicKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
