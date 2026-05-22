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
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ecgost.KeyPairGeneratorSpi;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCECGOST3410PrivateKeyTest {
    private static final String CURVE_NAME = "GostR3410-2001-CryptoPro-A";
    private static final byte[] RANDOM_SEED = {0x01, 0x23, 0x45, 0x67};

    @Test
    void keyPairPrivateKeySerializationWritesEncodedGostKey() throws Exception {
        ECPrivateKey privateKey = createPrivateKey();
        byte[] encodedPrivateKey = privateKey.getEncoded();

        byte[] serializedPrivateKey = serialize(privateKey, encodedPrivateKey);

        assertThat(privateKey).isInstanceOf(BCECGOST3410PrivateKey.class);
        assertThat(serializedPrivateKey).isNotEmpty();
    }

    @Test
    void keyPairPrivateKeySerializationRoundTripReadsEncodedGostKey() throws Exception {
        ECPrivateKey privateKey = createPrivateKey();
        byte[] encodedPrivateKey = privateKey.getEncoded();
        byte[] serializedPrivateKey = serialize(privateKey, encodedPrivateKey);

        ECPrivateKey restoredKey = (ECPrivateKey) deserialize(serializedPrivateKey, encodedPrivateKey);

        assertThat(restoredKey).isInstanceOf(BCECGOST3410PrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("ECGOST3410");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getS()).isEqualTo(privateKey.getS());
        assertThat(restoredKey.getParams()).isInstanceOf(ECNamedCurveSpec.class);
        assertThat(((ECNamedCurveSpec) restoredKey.getParams()).getName()).isEqualTo(CURVE_NAME);
        assertThat(restoredKey.getParams().getOrder()).isEqualTo(privateKey.getParams().getOrder());
        assertThat(restoredKey.getParams().getGenerator()).isEqualTo(privateKey.getParams().getGenerator());
        assertThat(restoredKey.getEncoded()).isEqualTo(encodedPrivateKey);
    }

    private static ECPrivateKey createPrivateKey() throws Exception {
        KeyPairGeneratorSpi generator = new KeyPairGeneratorSpi();
        generator.initialize(new ECGenParameterSpec(CURVE_NAME), new SecureRandom(RANDOM_SEED));
        KeyPair keyPair = generator.generateKeyPair();

        return (ECPrivateKey) keyPair.getPrivate();
    }

    private static byte[] serialize(Object value, byte[] encodedPrivateKey) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (TrackingObjectOutputStream objectStream = new TrackingObjectOutputStream(byteStream, encodedPrivateKey)) {
            objectStream.writeObject(value);
            assertThat(objectStream.wroteEncodedPrivateKey()).isTrue();
        }
        return byteStream.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue, byte[] encodedPrivateKey) throws Exception {
        try (TrackingObjectInputStream objectStream = new TrackingObjectInputStream(
                new ByteArrayInputStream(serializedValue), encodedPrivateKey)) {
            Object restored = objectStream.readObject();
            assertThat(objectStream.readEncodedPrivateKey()).isTrue();
            return restored;
        }
    }

    private static final class TrackingObjectOutputStream extends ObjectOutputStream {
        private final byte[] encodedPrivateKey;
        private boolean wroteEncodedPrivateKey;

        private TrackingObjectOutputStream(ByteArrayOutputStream outputStream, byte[] encodedPrivateKey)
                throws IOException {
            super(outputStream);
            this.encodedPrivateKey = encodedPrivateKey;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof byte[] && Arrays.equals(encodedPrivateKey, (byte[]) object)) {
                wroteEncodedPrivateKey = true;
            }
            return super.replaceObject(object);
        }

        private boolean wroteEncodedPrivateKey() {
            return wroteEncodedPrivateKey;
        }
    }

    private static final class TrackingObjectInputStream extends ObjectInputStream {
        private final byte[] encodedPrivateKey;
        private boolean readEncodedPrivateKey;

        private TrackingObjectInputStream(ByteArrayInputStream inputStream, byte[] encodedPrivateKey)
                throws IOException {
            super(inputStream);
            this.encodedPrivateKey = encodedPrivateKey;
            enableResolveObject(true);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDescription) throws IOException, ClassNotFoundException {
            if (BCECGOST3410PrivateKey.class.getName().equals(classDescription.getName())) {
                return BCECGOST3410PrivateKey.class;
            }
            return super.resolveClass(classDescription);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof byte[] && Arrays.equals(encodedPrivateKey, (byte[]) object)) {
                readEncodedPrivateKey = true;
            }
            return super.resolveObject(object);
        }

        private boolean readEncodedPrivateKey() {
            return readEncodedPrivateKey;
        }
    }
}
