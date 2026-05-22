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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.oiw.ElGamalParameter;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jcajce.provider.asymmetric.elgamal.BCElGamalPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.elgamal.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.ElGamalPrivateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCElGamalPrivateKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(6L);

    @Test
    void privateKeyInfoCreatedPrivateKeySerializationRoundTripPreservesElGamalParameters() throws Exception {
        ElGamalPrivateKey privateKey = createPrivateKey();

        SerializationResult serializedKey = serialize(privateKey);
        DeserializationResult deserializedKey = deserialize(serializedKey.serializedValue);
        ElGamalPrivateKey restoredKey = (ElGamalPrivateKey) deserializedKey.value;

        assertThat(privateKey).isInstanceOf(BCElGamalPrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCElGamalPrivateKey.class);
        assertThat(serializedKey.replacedObjects).contains(P, G);
        assertThat(deserializedKey.resolvedObjects).contains(P, G);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("ElGamal");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getX()).isEqualTo(X);
        assertThat(restoredKey.getParameters().getP()).isEqualTo(P);
        assertThat(restoredKey.getParameters().getG()).isEqualTo(G);
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    private static ElGamalPrivateKey createPrivateKey() throws Exception {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
                OIWObjectIdentifiers.elGamalAlgorithm,
                new ElGamalParameter(P, G).toASN1Primitive());
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(algorithmIdentifier, new DERInteger(X));

        return (ElGamalPrivateKey) new KeyFactorySpi().generatePrivate(privateKeyInfo);
    }

    private static SerializationResult serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        List<Object> replacedObjects;
        try (TrackingObjectOutputStream objectStream = new TrackingObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
            replacedObjects = objectStream.replacedObjects();
        }
        return new SerializationResult(byteStream.toByteArray(), replacedObjects);
    }

    private static DeserializationResult deserialize(byte[] serializedValue) throws Exception {
        try (TrackingObjectInputStream objectStream = new TrackingObjectInputStream(
                new ByteArrayInputStream(serializedValue))) {
            return new DeserializationResult(objectStream.readObject(), objectStream.resolvedObjects());
        }
    }

    private static final class SerializationResult {
        private final byte[] serializedValue;
        private final List<Object> replacedObjects;

        private SerializationResult(byte[] serializedValue, List<Object> replacedObjects) {
            this.serializedValue = serializedValue;
            this.replacedObjects = replacedObjects;
        }
    }

    private static final class DeserializationResult {
        private final Object value;
        private final List<Object> resolvedObjects;

        private DeserializationResult(Object value, List<Object> resolvedObjects) {
            this.value = value;
            this.resolvedObjects = resolvedObjects;
        }
    }

    private static final class TrackingObjectOutputStream extends ObjectOutputStream {
        private final List<Object> replacedObjects = new ArrayList<>();

        private TrackingObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            replacedObjects.add(object);
            return super.replaceObject(object);
        }

        private List<Object> replacedObjects() {
            return replacedObjects;
        }
    }

    private static final class TrackingObjectInputStream extends ObjectInputStream {
        private final List<Object> resolvedObjects = new ArrayList<>();

        private TrackingObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            resolvedObjects.add(object);
            return super.resolveObject(object);
        }

        private List<Object> resolvedObjects() {
            return resolvedObjects;
        }
    }
}
