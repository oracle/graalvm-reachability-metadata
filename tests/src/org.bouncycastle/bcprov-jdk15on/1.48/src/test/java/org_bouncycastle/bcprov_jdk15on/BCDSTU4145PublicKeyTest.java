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
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPublicKeySpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDSTU4145PublicKeyTest {
    private static final String DSTU4145_CURVE_OID = "1.2.804.2.1.1.1.1.3.1.1.2.0";

    @Test
    void specCreatedPublicKeySerializationRoundTripPreservesDstuParameters() throws Exception {
        ECPublicKey publicKey = createPublicKey();

        SerializationResult serializedKey = serialize(publicKey);
        DeserializationResult deserializedKey = deserialize(serializedKey.serializedValue);
        ECPublicKey restoredKey = (ECPublicKey) deserializedKey.value;

        assertThat(publicKey).isInstanceOf(BCDSTU4145PublicKey.class);
        assertThat(serializedKey.encodedKeyWritten).isTrue();
        assertThat(deserializedKey.encodedKeyRead).isTrue();
        assertThat(restoredKey).isInstanceOf(BCDSTU4145PublicKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSTU4145");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getW()).isEqualTo(publicKey.getW());
        assertThat(restoredKey.getParams()).isInstanceOf(ECNamedCurveSpec.class);
        assertThat(((ECNamedCurveSpec) restoredKey.getParams()).getName()).isEqualTo(DSTU4145_CURVE_OID);
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    private static ECPublicKey createPublicKey() {
        ECDomainParameters domainParameters = DSTU4145NamedCurves.getByOID(
                new ASN1ObjectIdentifier(DSTU4145_CURVE_OID));
        ECNamedCurveSpec namedCurveSpec = new ECNamedCurveSpec(
                DSTU4145_CURVE_OID,
                domainParameters.getCurve(),
                domainParameters.getG(),
                domainParameters.getN(),
                domainParameters.getH(),
                domainParameters.getSeed());
        ECPublicKeySpec keySpec = new ECPublicKeySpec(namedCurveSpec.getGenerator(), namedCurveSpec);

        return new BCDSTU4145PublicKey(keySpec);
    }

    private static SerializationResult serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (BouncyCastleObjectOutputStream objectStream = new BouncyCastleObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
            objectStream.flush();
            return new SerializationResult(byteStream.toByteArray(), objectStream.encodedKeyWritten());
        }
    }

    private static DeserializationResult deserialize(byte[] serializedValue) throws Exception {
        try (BouncyCastleObjectInputStream objectStream = new BouncyCastleObjectInputStream(
                new ByteArrayInputStream(serializedValue))) {
            return new DeserializationResult(objectStream.readObject(), objectStream.encodedKeyRead());
        }
    }

    private static final class SerializationResult {
        private final byte[] serializedValue;
        private final boolean encodedKeyWritten;

        private SerializationResult(byte[] serializedValue, boolean encodedKeyWritten) {
            this.serializedValue = serializedValue;
            this.encodedKeyWritten = encodedKeyWritten;
        }
    }

    private static final class DeserializationResult {
        private final Object value;
        private final boolean encodedKeyRead;

        private DeserializationResult(Object value, boolean encodedKeyRead) {
            this.value = value;
            this.encodedKeyRead = encodedKeyRead;
        }
    }

    private static final class BouncyCastleObjectOutputStream extends ObjectOutputStream {
        private boolean encodedKeyWritten;

        private BouncyCastleObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        private boolean encodedKeyWritten() {
            return encodedKeyWritten;
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof byte[]) {
                encodedKeyWritten = true;
            }
            return super.replaceObject(object);
        }
    }

    private static final class BouncyCastleObjectInputStream extends ObjectInputStream {
        private boolean encodedKeyRead;

        private BouncyCastleObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        private boolean encodedKeyRead() {
            return encodedKeyRead;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDescription) throws IOException, ClassNotFoundException {
            if (BCDSTU4145PublicKey.class.getName().equals(classDescription.getName())) {
                return BCDSTU4145PublicKey.class;
            }
            return super.resolveClass(classDescription);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof byte[]) {
                encodedKeyRead = true;
            }
            return super.resolveObject(object);
        }
    }
}
