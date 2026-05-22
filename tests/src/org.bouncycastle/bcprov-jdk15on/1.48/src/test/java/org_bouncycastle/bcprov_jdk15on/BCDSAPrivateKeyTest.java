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
import java.security.interfaces.DSAPrivateKey;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.BCDSAPrivateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDSAPrivateKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger X = BigInteger.valueOf(5L);

    @Test
    void privateKeyInfoCreatedPrivateKeySerializationRoundTripPreservesDsaParameters() throws Exception {
        DSAPrivateKey privateKey = createPrivateKey();

        DSAPrivateKey restoredKey = (DSAPrivateKey) deserialize(serialize(privateKey));

        assertThat(privateKey).isInstanceOf(BCDSAPrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCDSAPrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSA");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getX()).isEqualTo(X);
        assertThat(restoredKey.getParams().getP()).isEqualTo(P);
        assertThat(restoredKey.getParams().getQ()).isEqualTo(Q);
        assertThat(restoredKey.getParams().getG()).isEqualTo(G);
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    private static DSAPrivateKey createPrivateKey() throws Exception {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
                X9ObjectIdentifiers.id_dsa,
                new DSAParameter(P, Q, G).toASN1Primitive());
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(algorithmIdentifier, new ASN1Integer(X));

        return new BCDSAPrivateKey(privateKeyInfo);
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
            if (BCDSAPrivateKey.class.getName().equals(classDescription.getName())) {
                return BCDSAPrivateKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
