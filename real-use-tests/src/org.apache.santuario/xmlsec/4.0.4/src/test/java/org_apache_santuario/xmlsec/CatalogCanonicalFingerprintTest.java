/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/// Exercises exclusive XML canonicalization for stable catalog-import fingerprints across transport envelopes.
@Timeout(60)
public class CatalogCanonicalFingerprintTest {
    private static final String CATALOG_NAMESPACE = "urn:example:catalog";

    @Test
    public void deduplicatesEquivalentCatalogsButDetectsStockChanges() throws Exception {
        Init.init();
        Document original = parse("""
                <feed xmlns:c="urn:example:catalog" xmlns:route="urn:example:route:warehouse">
                    <c:Catalog revision="17"><c:Item sku="BOOK-17" quantity="8"/></c:Catalog>
                </feed>
                """);
        Document retransmitted = parse("""
                <delivery xmlns:route="urn:example:route:retailer" xmlns:c="urn:example:catalog">
                    <c:Catalog revision='17'><!-- retransmitted by gateway --><c:Item quantity='8' sku='B&#79;OK-17'></c:Item></c:Catalog>
                </delivery>
                """);

        // Only the catalog is the import identity; routing namespaces and comments are not business data.
        byte[] originalCanonical = canonicalCatalog(original);
        byte[] retransmittedCanonical = canonicalCatalog(retransmitted);
        assertEquals("""
                <c:Catalog xmlns:c="urn:example:catalog" revision="17"><c:Item quantity="8" sku="BOOK-17"></c:Item></c:Catalog>""",
                new String(originalCanonical, StandardCharsets.UTF_8));
        assertArrayEquals(originalCanonical, retransmittedCanonical);

        String importedFingerprint = fingerprint(originalCanonical);
        assertEquals(importedFingerprint, fingerprint(retransmittedCanonical),
                "A gateway's reserialization must not cause a duplicate catalog import");

        Element updatedItem = (Element) retransmitted.getElementsByTagNameNS(CATALOG_NAMESPACE, "Item").item(0);
        updatedItem.setAttribute("quantity", "9");
        byte[] updatedCanonical = canonicalCatalog(retransmitted);
        assertNotEquals(importedFingerprint, fingerprint(updatedCanonical),
                "A stock update must produce a new import identity");
        assertEquals("""
                <c:Catalog xmlns:c="urn:example:catalog" revision="17"><c:Item quantity="9" sku="BOOK-17"></c:Item></c:Catalog>""",
                new String(updatedCanonical, StandardCharsets.UTF_8));
    }

    private static Document parse(String xml) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return XMLUtils.read(input, true);
        }
    }

    private static byte[] canonicalCatalog(Document document) throws Exception {
        Element catalog = (Element) document.getElementsByTagNameNS(CATALOG_NAMESPACE, "Catalog").item(0);
        Canonicalizer canonicalizer = Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            canonicalizer.canonicalizeSubtree(catalog, output);
            return output.toByteArray();
        }
    }

    private static String fingerprint(byte[] canonicalXml) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalXml));
    }
}
