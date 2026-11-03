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
import org.apache.tika.metadata.filter.FieldNameMappingFilter;
import org.apache.tika.metadata.filter.IncludeFieldMetadataFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Tika's metadata filtering subsystem for reducing extracted fields before indexing.
public class DocumentMetadataFilteringTest {
    @Test
    @Timeout(60)
    public void keepsAndRenamesTheFieldsAnIndexAccepts() throws Exception {
        Metadata extracted = new Metadata();
        extracted.set(TikaCoreProperties.TITLE, "Quarterly invoice");
        extracted.add(DublinCore.CREATOR, "Accounts payable");
        extracted.add(DublinCore.CREATOR, "Finance operations");
        extracted.add(DublinCore.SUBJECT, "invoice");
        extracted.set(HttpHeaders.CONTENT_TYPE, "application/pdf");

        IncludeFieldMetadataFilter selectedFields = new IncludeFieldMetadataFilter(Set.of(
                TikaCoreProperties.TITLE.getName(), DublinCore.CREATOR.getName()));
        selectedFields.filter(extracted);

        FieldNameMappingFilter indexFields = new FieldNameMappingFilter();
        indexFields.setMappings(Map.of(
                TikaCoreProperties.TITLE.getName(), "index.title",
                DublinCore.CREATOR.getName(), "index.author"));
        indexFields.filter(extracted);

        assertEquals("Quarterly invoice", extracted.get("index.title"));
        assertArrayEquals(new String[] {"Accounts payable", "Finance operations"},
                extracted.getValues("index.author"));
        assertNull(extracted.get(TikaCoreProperties.TITLE));
        assertNull(extracted.get(DublinCore.SUBJECT));
        assertNull(extracted.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(2, extracted.names().length);
    }
}
