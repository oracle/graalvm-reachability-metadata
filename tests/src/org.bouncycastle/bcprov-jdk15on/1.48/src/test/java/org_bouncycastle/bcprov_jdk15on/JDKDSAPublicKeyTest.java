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
import java.io.Serializable;
import java.math.BigInteger;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAParameterSpec;

import org.bouncycastle.jce.provider.JDKDSAPublicKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JDKDSAPublicKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger Y = BigInteger.valueOf(4L);

    @Test
    void serializationRoundTripPreservesDsaParameters() throws Exception {
        DSAPublicKey publicKey = (DSAPublicKey) deserialize(serializedJdkDsaPublicKey());

        DSAPublicKey restoredKey = (DSAPublicKey) deserialize(serialize(publicKey));

        assertThat(publicKey).isInstanceOf(JDKDSAPublicKey.class);
        assertThat(restoredKey).isInstanceOf(JDKDSAPublicKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSA");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getY()).isEqualTo(Y);
        assertThat(restoredKey.getParams().getP()).isEqualTo(P);
        assertThat(restoredKey.getParams().getQ()).isEqualTo(Q);
        assertThat(restoredKey.getParams().getG()).isEqualTo(G);
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    private static byte[] serializedJdkDsaPublicKey() throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new JdkDsaPublicKeyObjectOutputStream(byteStream)) {
            objectStream.writeObject(new SerializedJdkDsaPublicKeyShape());
        }
        return byteStream.toByteArray();
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
            if (JDKDSAPublicKey.class.getName().equals(classDescription.getName())) {
                return JDKDSAPublicKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }

    private static final class JdkDsaPublicKeyObjectOutputStream extends ObjectOutputStream {
        private JdkDsaPublicKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJdkDsaPublicKeyShape.class.getName().equals(descriptor.getName())) {
                super.writeClassDescriptor(ObjectStreamClass.lookup(JDKDSAPublicKey.class));
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }

    private static final class SerializedJdkDsaPublicKeyShape implements Serializable {
        private static final long serialVersionUID = 1L;

        private transient BigInteger y = Y;
        private transient DSAParams dsaSpec = new DSAParameterSpec(P, Q, G);

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(y);
            out.writeObject(dsaSpec.getP());
            out.writeObject(dsaSpec.getQ());
            out.writeObject(dsaSpec.getG());
        }
    }
}
