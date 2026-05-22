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
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.ecgost.KeyPairGeneratorSpi;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCECGOST3410PublicKeyTest {
    private static final String CURVE_NAME = "GostR3410-2001-CryptoPro-A";
    private static final byte[] RANDOM_SEED = {0x10, 0x32, 0x54, 0x76};

    @Test
    void keyPairPublicKeySerializationWritesEncodedGostKey() throws Exception {
        BCECGOST3410PublicKey publicKey = createPublicKey();

        byte[] serializedPublicKey = serialize(publicKey);

        assertThat(publicKey.getAlgorithm()).isEqualTo("ECGOST3410");
        assertThat(serializedPublicKey).isNotEmpty();
    }

    @Test
    void keyPairPublicKeySerializationRoundTripReadsEncodedGostKey() throws Exception {
        BCECGOST3410PublicKey publicKey = createPublicKey();
        byte[] encodedPublicKey = publicKey.getEncoded();
        byte[] serializedPublicKey = serialize(publicKey);

        BCECGOST3410PublicKey restoredKey = (BCECGOST3410PublicKey) deserialize(serializedPublicKey);

        assertThat(restoredKey).isNotSameAs(publicKey);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("ECGOST3410");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getW()).isEqualTo(publicKey.getW());
        assertThat(restoredKey.getParams()).isInstanceOf(ECNamedCurveSpec.class);
        assertThat(((ECNamedCurveSpec) restoredKey.getParams()).getName()).isEqualTo(CURVE_NAME);
        assertThat(restoredKey.getParams().getOrder()).isEqualTo(publicKey.getParams().getOrder());
        assertThat(restoredKey.getParams().getGenerator()).isEqualTo(publicKey.getParams().getGenerator());
        assertThat(restoredKey.getEncoded()).isEqualTo(encodedPublicKey);
    }

    private static BCECGOST3410PublicKey createPublicKey() throws Exception {
        KeyPairGeneratorSpi generator = new KeyPairGeneratorSpi();
        generator.initialize(new ECGenParameterSpec(CURVE_NAME), new SecureRandom(RANDOM_SEED));
        KeyPair keyPair = generator.generateKeyPair();

        return (BCECGOST3410PublicKey) keyPair.getPublic();
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
