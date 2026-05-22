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
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECPrivateKeySpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PrivateKey;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDSTU4145PrivateKeyTest {
    private static final String DSTU4145_CURVE_OID = "1.2.804.2.1.1.1.1.3.1.1.2.0";
    private static final BigInteger PRIVATE_VALUE = BigInteger.ONE;

    @Test
    void specCreatedPrivateKeySerializationRoundTripPreservesDstuParameters() throws Exception {
        ECPrivateKey privateKey = createPrivateKey();

        ECPrivateKey restoredKey = (ECPrivateKey) deserialize(serialize(privateKey));

        assertThat(privateKey).isInstanceOf(BCDSTU4145PrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCDSTU4145PrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSTU4145");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getS()).isEqualTo(PRIVATE_VALUE);
        assertThat(restoredKey.getParams()).isInstanceOf(ECNamedCurveSpec.class);
        assertThat(((ECNamedCurveSpec) restoredKey.getParams()).getName()).isEqualTo(DSTU4145_CURVE_OID);
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    private static ECPrivateKey createPrivateKey() {
        ECDomainParameters domainParameters = DSTU4145NamedCurves.getByOID(
                new ASN1ObjectIdentifier(DSTU4145_CURVE_OID));
        ECNamedCurveSpec namedCurveSpec = new ECNamedCurveSpec(
                DSTU4145_CURVE_OID,
                domainParameters.getCurve(),
                domainParameters.getG(),
                domainParameters.getN(),
                domainParameters.getH(),
                domainParameters.getSeed());
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(PRIVATE_VALUE, namedCurveSpec);

        return new BCDSTU4145PrivateKey(keySpec);
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
            if (BCDSTU4145PrivateKey.class.getName().equals(classDescription.getName())) {
                return BCDSTU4145PrivateKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
