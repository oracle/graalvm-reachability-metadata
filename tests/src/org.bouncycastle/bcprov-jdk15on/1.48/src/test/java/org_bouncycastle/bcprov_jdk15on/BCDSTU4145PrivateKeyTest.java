/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PrivateKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.PrivateKey;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDSTU4145PrivateKeyTest {
    private static final BigInteger D = new BigInteger("123456789abcdef", 16);

    @Test
    public void serializesDstuPrivateKeyWithExplicitParameters() throws Exception {
        BCDSTU4145PrivateKey originalKey = createPrivateKey();

        byte[] encodedKey = originalKey.getEncoded();
        byte[] serializedKey = serialize(originalKey);

        assertThat(encodedKey).isNotEmpty();
        assertThat(serializedKey).isNotEmpty();
    }

    @Test
    public void deserializesDstuPrivateKeyWithExplicitParameters() throws Exception {
        BCDSTU4145PrivateKey originalKey = createPrivateKey();
        byte[] serializedKey = serialize(originalKey);

        BCDSTU4145PrivateKey restoredKey = deserialize(serializedKey);

        assertThat(restoredKey.getD()).isEqualTo(originalKey.getD());
        assertThat(restoredKey.getS()).isEqualTo(originalKey.getS());
        assertThat(restoredKey.getParams()).isNotNull();
        assertThat(restoredKey.getParameters()).isNotNull();
        assertThat(restoredKey.getEncoded()).isEqualTo(originalKey.getEncoded());
        assertThat(restoredKey.getAlgorithm()).isEqualTo("DSTU4145");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
    }

    private static BCDSTU4145PrivateKey createPrivateKey() {
        ECDomainParameters parameters = DSTU4145NamedCurves.params[0];
        ECParameterSpec parameterSpec = new ECParameterSpec(
                parameters.getCurve(),
                parameters.getG(),
                parameters.getN(),
                parameters.getH(),
                parameters.getSeed());
        return new BCDSTU4145PrivateKey(new ECPrivateKeySpec(D, parameterSpec));
    }

    private static byte[] serialize(PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(privateKey);
        }
        assertThat(bytes.size()).isGreaterThan(0);
        return bytes.toByteArray();
    }

    private static BCDSTU4145PrivateKey deserialize(byte[] serializedKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedKey))) {
            return (BCDSTU4145PrivateKey) input.readObject();
        }
    }
}
