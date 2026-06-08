/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.junit.jupiter.api.Test;

public class BCDSTU4145PublicKeyTest {
    private static final String BCDSTU4145_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PublicKey";
    private static final BigInteger PUBLIC_MULTIPLIER = BigInteger.valueOf(7L);

    @Test
    void javaSerializationPreservesDstu4145PublicKeyParameters() throws Exception {
        BCDSTU4145PublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        ECPublicKey restored = deserialize(serialized);

        assertEquals(BCDSTU4145_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCDSTU4145_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey.getW(), restored.getW());
        assertParameters(publicKey.getParams(), restored.getParams());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    private static BCDSTU4145PublicKey generatePublicKey() throws Exception {
        ASN1ObjectIdentifier curveOid = DSTU4145NamedCurves.getOIDs()[0];
        ECDomainParameters domainParameters = DSTU4145NamedCurves.getByOID(curveOid);
        ECNamedCurveParameterSpec curveSpec = new ECNamedCurveParameterSpec(
            curveOid.getId(),
            domainParameters.getCurve(),
            domainParameters.getG(),
            domainParameters.getN(),
            domainParameters.getH(),
            domainParameters.getSeed());
        return new BCDSTU4145PublicKey(
            new ECPublicKeySpec(
                domainParameters.getG().multiply(PUBLIC_MULTIPLIER).normalize(),
                curveSpec),
            BouncyCastleProvider.CONFIGURATION);
    }

    private static void assertParameters(
            ECParameterSpec expected,
            ECParameterSpec actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getOrder(), actual.getOrder());
        assertEquals(expected.getCofactor(), actual.getCofactor());
        assertEquals(expected.getCurve(), actual.getCurve());
        assertEquals(expected.getGenerator(), actual.getGenerator());
    }

    private static byte[] serialize(ECPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPublicKey)objectInputStream.readObject();
        }
    }
}
