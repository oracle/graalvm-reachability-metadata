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
import java.nio.charset.StandardCharsets;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.spec.DSAParameterSpec;
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.jce.provider.JDKDSAPrivateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JDKDSAPrivateKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger X = BigInteger.valueOf(5L);

    @Test
    void serializationRoundTripPreservesDsaParameters() throws Exception {
        DSAPrivateKey privateKey = deserializeSeedKey();

        DSAPrivateKey restoredKey = (DSAPrivateKey) deserialize(serialize(privateKey));

        assertThat(privateKey).isInstanceOf(JDKDSAPrivateKey.class);
        assertThat(restoredKey).isInstanceOf(JDKDSAPrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSA");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getX()).isEqualTo(X);
        assertThat(restoredKey.getParams().getP()).isEqualTo(P);
        assertThat(restoredKey.getParams().getQ()).isEqualTo(Q);
        assertThat(restoredKey.getParams().getG()).isEqualTo(G);
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    private static DSAPrivateKey deserializeSeedKey() throws Exception {
        byte[] seedStream = serialize(new SerializationSeed());
        byte[] keyStream = replaceClassDescriptor(
                seedStream,
                SerializationSeed.class,
                JDKDSAPrivateKey.class);

        return (DSAPrivateKey) deserialize(keyStream);
    }

    private static byte[] replaceClassDescriptor(byte[] stream, Class<?> oldClass, Class<?> newClass) {
        byte[] oldNameBytes = descriptorName(oldClass.getName());
        byte[] newNameBytes = descriptorName(newClass.getName());
        int descriptorNameIndex = indexOf(stream, oldNameBytes);
        assertThat(descriptorNameIndex).isNotNegative();

        long serialVersionUid = ObjectStreamClass.lookup(newClass).getSerialVersionUID();
        int oldSerialVersionUidIndex = descriptorNameIndex + oldNameBytes.length;
        int afterOldSerialVersionUidIndex = oldSerialVersionUidIndex + Long.BYTES;

        ByteArrayOutputStream output = new ByteArrayOutputStream(
                stream.length - oldNameBytes.length + newNameBytes.length);
        output.write(stream, 0, descriptorNameIndex);
        output.write(newNameBytes, 0, newNameBytes.length);
        writeLong(output, serialVersionUid);
        output.write(
                stream,
                afterOldSerialVersionUidIndex,
                stream.length - afterOldSerialVersionUidIndex);
        return output.toByteArray();
    }

    private static byte[] descriptorName(String className) {
        byte[] nameBytes = className.getBytes(StandardCharsets.UTF_8);
        assertThat(nameBytes.length).isLessThanOrEqualTo(0xFFFF);

        byte[] descriptorName = new byte[nameBytes.length + 2];
        descriptorName[0] = (byte) (nameBytes.length >>> Byte.SIZE);
        descriptorName[1] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, descriptorName, 2, nameBytes.length);
        return descriptorName;
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            output.write((byte) (value >>> shift));
        }
    }

    private static int indexOf(byte[] bytes, byte[] pattern) {
        for (int i = 0; i <= bytes.length - pattern.length; i++) {
            boolean matches = true;
            for (int j = 0; j < pattern.length; j++) {
                if (bytes[i + j] != pattern[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }
        return -1;
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

    private static final class SerializationSeed implements Serializable {
        private static final long serialVersionUID = 1L;

        private transient BigInteger x = X;
        private transient DSAParams dsaSpec = new DSAParameterSpec(P, Q, G);

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(x);
            out.writeObject(dsaSpec.getP());
            out.writeObject(dsaSpec.getQ());
            out.writeObject(dsaSpec.getG());
            out.writeObject(new Hashtable<>());
            out.writeObject(new Vector<>());
        }
    }

    private static final class BouncyCastleObjectInputStream extends ObjectInputStream {
        private BouncyCastleObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDescription) throws IOException, ClassNotFoundException {
            if (JDKDSAPrivateKey.class.getName().equals(classDescription.getName())) {
                return JDKDSAPrivateKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
