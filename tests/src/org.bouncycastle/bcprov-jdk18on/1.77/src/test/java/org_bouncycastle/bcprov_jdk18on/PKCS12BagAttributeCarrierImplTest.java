/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.util.PKCS12BagAttributeCarrierImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PKCS12BagAttributeCarrierImplTest {
    @Test
    void persistsEmptyAndNamedBags() throws Exception {
        PKCS12BagAttributeCarrierImpl empty = copy(new PKCS12BagAttributeCarrierImpl());
        assertFalse(empty.getBagAttributeKeys().hasMoreElements());

        PKCS12BagAttributeCarrierImpl named = new PKCS12BagAttributeCarrierImpl();
        DERBMPString name = new DERBMPString("signing key");
        named.setBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, name);
        PKCS12BagAttributeCarrierImpl restored = copy(named);
        assertEquals(name, restored.getBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName));
        assertEquals(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, restored.getBagAttributeKeys().nextElement());
    }

    private static PKCS12BagAttributeCarrierImpl copy(PKCS12BagAttributeCarrierImpl bag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            bag.writeObject(output);
        }
        PKCS12BagAttributeCarrierImpl restored = new PKCS12BagAttributeCarrierImpl();
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObject(input);
        }
        return restored;
    }
}
