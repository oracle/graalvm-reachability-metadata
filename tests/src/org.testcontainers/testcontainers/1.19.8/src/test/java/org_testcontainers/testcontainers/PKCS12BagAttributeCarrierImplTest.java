/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.testcontainers.shaded.org.bouncycastle.asn1.DERUTF8String;
import org.testcontainers.shaded.org.bouncycastle.jcajce.provider.asymmetric.util.PKCS12BagAttributeCarrierImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class PKCS12BagAttributeCarrierImplTest {
    @Test
    void writesAndReadsBagAttributesThroughItsPersistenceApi() throws Exception {
        ASN1ObjectIdentifier identifier = new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");
        PKCS12BagAttributeCarrierImpl original = new PKCS12BagAttributeCarrierImpl();
        original.setBagAttribute(identifier, new DERUTF8String("friendly-name"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            original.writeObject(output);
        }

        PKCS12BagAttributeCarrierImpl restored = new PKCS12BagAttributeCarrierImpl();
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObject(input);
        }

        assertThat(restored.getBagAttribute(identifier).toString()).isEqualTo("friendly-name");
    }
}
