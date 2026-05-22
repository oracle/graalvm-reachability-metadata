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

import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.oiw.ElGamalParameter;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.elgamal.BCElGamalPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.elgamal.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.ElGamalPublicKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCElGamalPublicKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void subjectPublicKeyInfoCreatedPublicKeySerializationRoundTripPreservesElGamalParameters() throws Exception {
        ElGamalPublicKey publicKey = createPublicKey();

        ElGamalPublicKey restoredKey = (ElGamalPublicKey) deserialize(serialize(publicKey));

        assertThat(publicKey).isInstanceOf(BCElGamalPublicKey.class);
        assertThat(restoredKey).isInstanceOf(BCElGamalPublicKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("ElGamal");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getY()).isEqualTo(Y);
        assertThat(restoredKey.getParameters().getP()).isEqualTo(P);
        assertThat(restoredKey.getParameters().getG()).isEqualTo(G);
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    private static ElGamalPublicKey createPublicKey() throws Exception {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
                OIWObjectIdentifiers.elGamalAlgorithm,
                new ElGamalParameter(P, G).toASN1Primitive());
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(algorithmIdentifier, new DERInteger(Y));

        return (ElGamalPublicKey) new KeyFactorySpi().generatePublic(publicKeyInfo);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        try {
            return deserializeWithDefaultClassResolution(serializedValue);
        } catch (ClassNotFoundException e) {
            return deserializeWithBouncyCastleClassResolution(serializedValue);
        }
    }

    private static Object deserializeWithDefaultClassResolution(byte[] serializedValue) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
            return objectStream.readObject();
        }
    }

    private static Object deserializeWithBouncyCastleClassResolution(byte[] serializedValue) throws Exception {
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
            if (BCElGamalPublicKey.class.getName().equals(classDescription.getName())) {
                return BCElGamalPublicKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
