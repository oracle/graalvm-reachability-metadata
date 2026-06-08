/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;

import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHPublicKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCDHPublicKeyTest {
    private static final String BCDH_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.dh.BCDHPublicKey";
    private static final BigInteger P = new BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
            + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6"
            + "D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6"
            + "F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6"
            + "49286651ECE65381FFFFFFFFFFFFFFFF",
        16);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger Y = BigInteger.valueOf(4L);

    @Test
    void javaSerializationPreservesDiffieHellmanPublicKeyParameters() throws Exception {
        DHPublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        DHPublicKey restored = deserialize(serialized);

        assertEquals(BCDH_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCDH_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getY(), restored.getY());
        assertEquals(publicKey.getParams().getP(), restored.getParams().getP());
        assertEquals(publicKey.getParams().getG(), restored.getParams().getG());
        assertEquals(publicKey.getParams().getL(), restored.getParams().getL());
    }

    private static DHPublicKey generatePublicKey() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("DH", new BouncyCastleProvider());
        PublicKey publicKey = keyFactory.generatePublic(new DHPublicKeySpec(Y, P, G));
        return (DHPublicKey)publicKey;
    }

    private static byte[] serialize(DHPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static DHPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (DHPublicKey)objectInputStream.readObject();
        }
    }
}
