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
import java.security.KeyFactory;
import java.security.Provider;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHPrivateKeySpec;

import org.bouncycastle.jcajce.provider.asymmetric.dh.BCDHPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDHPrivateKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(6L);

    @Test
    void keyFactoryCreatedPrivateKeySerializationRoundTripPreservesDhParameters() throws Exception {
        DHPrivateKey privateKey = createPrivateKey();

        DHPrivateKey restoredKey = (DHPrivateKey) deserialize(serialize(privateKey));

        assertThat(privateKey).isInstanceOf(BCDHPrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCDHPrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DH");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getX()).isEqualTo(X);
        assertThat(restoredKey.getParams().getP()).isEqualTo(P);
        assertThat(restoredKey.getParams().getG()).isEqualTo(G);
        assertThat(restoredKey.getParams().getL()).isZero();
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    private static DHPrivateKey createPrivateKey() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyFactory keyFactory = KeyFactory.getInstance("DH", provider);
        DHPrivateKeySpec keySpec = new DHPrivateKeySpec(X, P, G);

        return (DHPrivateKey) keyFactory.generatePrivate(keySpec);
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
