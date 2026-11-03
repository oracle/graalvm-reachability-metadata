/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Tika's typed document metadata subsystem for an indexing record.
public class DocumentMetadataCatalogTest {
    @Test
    @Timeout(60)
    public void catalogsTypedAndRepeatedDocumentProperties() {
        Metadata metadata = new Metadata();
        Date created = Date.from(Instant.parse("2024-04-12T09:30:00Z"));

        metadata.set(TikaCoreProperties.TITLE, "Quarterly invoice");
        metadata.set(DublinCore.CREATOR, "Accounts payable");
        metadata.set(TikaCoreProperties.NUM_IMAGES, 2);
        metadata.set(TikaCoreProperties.CREATED, created);
        metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf");
        metadata.add(DublinCore.SUBJECT, "invoice");
        metadata.add(DublinCore.SUBJECT, "quarterly");

        assertEquals("Quarterly invoice", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Accounts payable", metadata.get(DublinCore.CREATOR));
        assertEquals(2, metadata.getInt(TikaCoreProperties.NUM_IMAGES));
        assertEquals(created, metadata.getDate(TikaCoreProperties.CREATED));
        assertEquals("application/pdf", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertArrayEquals(new String[] {"invoice", "quarterly"},
                metadata.getValues(DublinCore.SUBJECT));
        assertTrue(metadata.isMultiValued(DublinCore.SUBJECT));
        assertTrue(metadata.names().length >= 6);
    }
}
