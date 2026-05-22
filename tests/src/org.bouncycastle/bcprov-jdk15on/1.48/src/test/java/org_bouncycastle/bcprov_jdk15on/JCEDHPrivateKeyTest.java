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
import java.util.Base64;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHParameterSpec;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.jce.provider.JCEDHPrivateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCEDHPrivateKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(6L);

    @Test
    void serializedJcePrivateKeyRoundTripPreservesDhParameters() throws Exception {
        DHPrivateKey privateKey = (DHPrivateKey) deserialize(serializedJcePrivateKey());

        DHPrivateKey restoredKey = (DHPrivateKey) deserialize(serialize(privateKey));

        assertThat(privateKey).isInstanceOf(JCEDHPrivateKey.class);
        assertThat(restoredKey).isInstanceOf(JCEDHPrivateKey.class);
        assertJcePrivateKey(restoredKey);
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
    }

    @Test
    void serializedHolderRoundTripPreservesNestedJcePrivateKey() throws Exception {
        DHPrivateKey privateKey = (DHPrivateKey) deserialize(serializedJcePrivateKey());
        KeyHolder holder = new KeyHolder(privateKey);

        KeyHolder restoredHolder = (KeyHolder) deserialize(serialize(holder));

        assertThat(restoredHolder.privateKey).isInstanceOf(JCEDHPrivateKey.class);
        assertJcePrivateKey(restoredHolder.privateKey);
    }

    private static void assertJcePrivateKey(DHPrivateKey privateKey) {
        assertThat(privateKey.getAlgorithm()).isEqualTo("DH");
        assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(privateKey.getX()).isEqualTo(X);
        assertThat(privateKey.getParams().getP()).isEqualTo(P);
        assertThat(privateKey.getParams().getG()).isEqualTo(G);
        assertThat(privateKey.getParams().getL()).isZero();
    }

    private static byte[] serializedJcePrivateKey() {
        return Base64.getMimeDecoder().decode("""
                rO0ABXNyAC1vcmcuYm91bmN5Y2FzdGxlLmpjZS5wcm92aWRlci5KQ0VESFByaXZhdGVLZXkEURpY
                QRlitAMABEwAC2F0dHJDYXJyaWVydAA7TG9yZy9ib3VuY3ljYXN0bGUvamNlL2ludGVyZmFjZXMv
                UEtDUzEyQmFnQXR0cmlidXRlQ2FycmllcjtMAAZkaFNwZWN0ACNMamF2YXgvY3J5cHRvL3NwZWMv
                REhQYXJhbWV0ZXJTcGVjO0wABGluZm90ACtMb3JnL2JvdW5jeWNhc3RsZS9hc24xL3BrY3MvUHJp
                dmF0ZUtleUluZm87TAABeHQAFkxqYXZhL21hdGgvQmlnSW50ZWdlcjt4cHNyABRqYXZhLm1hdGgu
                QmlnSW50ZWdlcoz8nx+pO/sdAwAGSQAIYml0Q291bnRJAAliaXRMZW5ndGhJABNmaXJzdE5vbnpl
                cm9CeXRlTnVtSQAMbG93ZXN0U2V0Qml0SQAGc2lnbnVtWwAJbWFnbml0dWRldAACW0J4cgAQamF2
                YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHD///////////////7////+AAAAAXVyAAJbQqzzF/gG
                CFTgAgAAeHAAAAABBnhzcQB+AAb///////////////7////+AAAAAXVxAH4ACgAAAAEXeHNxAH4A
                Bv///////////////v////4AAAABdXEAfgAKAAAAAQV4dwQAAAAAeA==
                """);
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

    private static final class KeyHolder implements Serializable {
        private static final long serialVersionUID = 1L;

        private final DHPrivateKey privateKey;

        private KeyHolder(DHPrivateKey privateKey) {
            this.privateKey = privateKey;
        }
    }

    private static final class BouncyCastleObjectInputStream extends ObjectInputStream {
        private BouncyCastleObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDescription) throws IOException, ClassNotFoundException {
            if (JCEDHPrivateKey.class.getName().equals(classDescription.getName())) {
                return JCEDHPrivateKey.class;
            }
            if (PKCS12BagAttributeCarrier.class.getName().equals(classDescription.getName())) {
                return PKCS12BagAttributeCarrier.class;
            }
            if (PrivateKeyInfo.class.getName().equals(classDescription.getName())) {
                return PrivateKeyInfo.class;
            }
            if (DHParameterSpec.class.getName().equals(classDescription.getName())) {
                return DHParameterSpec.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
