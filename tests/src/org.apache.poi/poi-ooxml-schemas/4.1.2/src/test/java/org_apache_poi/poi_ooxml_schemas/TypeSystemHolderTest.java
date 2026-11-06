/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml_schemas;

import org.apache.xmlbeans.XmlException;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.xpackage.x2006.digitalSignature.CTSignatureTime;
import org.openxmlformats.schemas.xpackage.x2006.digitalSignature.SignatureTimeDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeSystemHolderTest {
    @Test
    void roundTripsPackageSignatureTime() throws XmlException {
        SignatureTimeDocument document = SignatureTimeDocument.Factory.newInstance();
        CTSignatureTime signatureTime = document.addNewSignatureTime();
        signatureTime.setFormat("YYYY-MM-DDThh:mm:ssTZD");
        signatureTime.setValue("2025-06-18T10:30:00Z");

        assertTrue(document.validate());

        SignatureTimeDocument parsed = SignatureTimeDocument.Factory.parse(document.xmlText());
        assertEquals("YYYY-MM-DDThh:mm:ssTZD", parsed.getSignatureTime().getFormat());
        assertEquals("2025-06-18T10:30:00Z", parsed.getSignatureTime().getValue());
        assertTrue(parsed.validate());
    }
}
