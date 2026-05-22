/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCECPrivateKeyTest {
    private static final String CURVE_NAME = "prime256v1";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(42L);

    @Test
    void constructorCreatedPrivateKeySerializationRoundTripPreservesEcParameters() throws Exception {
        BCECPrivateKey privateKey = createPrivateKey();

        ECPrivateKey restoredKey = (ECPrivateKey) deserialize(serialize(privateKey));

        assertThat(privateKey).isInstanceOf(BCECPrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCECPrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("EC");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getS()).isEqualTo(PRIVATE_VALUE);
        assertThat(restoredKey.getParams()).isNotNull();
        assertThat(restoredKey.getParams().getOrder()).isEqualTo(privateKey.getParams().getOrder());
        assertThat(restoredKey.getParams().getGenerator()).isEqualTo(privateKey.getParams().getGenerator());
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    private static BCECPrivateKey createPrivateKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(PRIVATE_VALUE, curveSpec);

        return new BCECPrivateKey("EC", keySpec, BouncyCastleProvider.CONFIGURATION);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
            return objectStream.readObject();
        }
    }
}
