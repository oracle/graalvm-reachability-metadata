/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.crypto.asymmetric.ECDomainParameters;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.DSTU4145ParameterSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvDSTU4145PrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesADstu4145PrivateKey() throws Exception {
        KeyPair keyPair = generateDstu4145KeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PrivateKey restoredPrivateKey = deserialize(serialize(privateKey));

        assertThat(restoredPrivateKey.getAlgorithm()).isEqualTo("DSTU4145");
        assertThat(restoredPrivateKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(signAndVerify(restoredPrivateKey, keyPair)).isTrue();
    }

    private KeyPair generateDstu4145KeyPair() throws Exception {
        ASN1ObjectIdentifier curveOid = DSTU4145NamedCurves.getOIDs()[0];
        ECDomainParameters parameters = DSTU4145NamedCurves.getByOID(curveOid);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "DSTU4145", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(new DSTU4145ParameterSpec(parameters));
        return generator.generateKeyPair();
    }

    private byte[] serialize(PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(privateKey);
        }
        return bytes.toByteArray();
    }

    private PrivateKey deserialize(byte[] serializedPrivateKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedPrivateKey))) {
            return (PrivateKey) input.readObject();
        }
    }

    private boolean signAndVerify(PrivateKey privateKey, KeyPair keyPair) throws Exception {
        byte[] message = "serialized DSTU4145 private key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "GOST3411WITHDSTU4145", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(privateKey);
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "GOST3411WITHDSTU4145", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
