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

import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;

import org.bouncycastle.jce.provider.JCEDHPublicKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCEDHPublicKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void serializedJcePublicKeyRoundTripPreservesDhParameters() throws Exception {
        JCEDHPublicKey publicKey = (JCEDHPublicKey) deserialize(serializedJcePublicKey());

        JCEDHPublicKey restoredKey = (JCEDHPublicKey) deserialize(serialize(publicKey));

        assertJcePublicKey(restoredKey);
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    @Test
    void serializedHolderRoundTripPreservesNestedJcePublicKey() throws Exception {
        JCEDHPublicKey publicKey = (JCEDHPublicKey) deserialize(serializedJcePublicKey());
        KeyHolder holder = new KeyHolder(publicKey);

        KeyHolder restoredHolder = (KeyHolder) deserialize(serialize(holder));

        assertThat(restoredHolder.publicKey).isInstanceOf(JCEDHPublicKey.class);
        assertJcePublicKey(restoredHolder.publicKey);
    }

    private static void assertJcePublicKey(DHPublicKey publicKey) {
        assertThat(publicKey.getAlgorithm()).isEqualTo("DH");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getY()).isEqualTo(Y);
        assertThat(publicKey.getParams().getP()).isEqualTo(P);
        assertThat(publicKey.getParams().getG()).isEqualTo(G);
        assertThat(publicKey.getParams().getL()).isZero();
    }

    private static byte[] serializedJcePublicKey() throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new JcePublicKeyObjectOutputStream(byteStream)) {
            objectStream.writeObject(new SerializedJcePublicKeyShape());
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
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
            return objectStream.readObject();
        }
    }

    private static final class JcePublicKeyObjectOutputStream extends ObjectOutputStream {
        private JcePublicKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJcePublicKeyShape.class.getName().equals(descriptor.getName())) {
                super.writeClassDescriptor(ObjectStreamClass.lookup(JCEDHPublicKey.class));
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }

    private static final class SerializedJcePublicKeyShape implements DHPublicKey {
        private static final long serialVersionUID = 1L;

        @Override
        public String getAlgorithm() {
            return "DH";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }

        @Override
        public DHParameterSpec getParams() {
            return new DHParameterSpec(P, G);
        }

        @Override
        public BigInteger getY() {
            return Y;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(Y);
            out.writeObject(P);
            out.writeObject(G);
            out.writeInt(0);
        }
    }

    private static final class KeyHolder implements Serializable {
        private static final long serialVersionUID = 1L;

        private final DHPublicKey publicKey;

        private KeyHolder(DHPublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }
}
