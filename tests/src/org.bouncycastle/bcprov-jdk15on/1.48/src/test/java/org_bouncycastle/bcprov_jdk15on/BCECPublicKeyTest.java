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

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCECPublicKeyTest {
    private static final String CURVE_NAME = "prime256v1";

    @Test
    void constructorCreatedPublicKeySerializationRoundTripPreservesEcParameters() throws Exception {
        BCECPublicKey publicKey = createPublicKey();

        BCECPublicKey restoredKey = (BCECPublicKey) deserialize(serialize(publicKey));

        assertThat(publicKey).isInstanceOf(BCECPublicKey.class);
        assertThat(restoredKey).isInstanceOf(BCECPublicKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("EC");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getW()).isEqualTo(publicKey.getW());
        assertThat(restoredKey.getParams()).isNotNull();
        assertThat(restoredKey.getParams().getOrder()).isEqualTo(publicKey.getParams().getOrder());
        assertThat(restoredKey.getParams().getGenerator()).isEqualTo(publicKey.getParams().getGenerator());
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    private static BCECPublicKey createPublicKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(curveSpec.getG(), curveSpec);

        return new BCECPublicKey("EC", keySpec, BouncyCastleProvider.CONFIGURATION);
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
