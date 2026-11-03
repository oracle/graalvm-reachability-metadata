/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.utils.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Tika's document date normalization subsystem for an indexing pipeline.
public class DocumentDateNormalizationTest {
    @Test
    @Timeout(60)
    public void normalizesExtractedDatesBeforeIndexing() {
        DateUtils dateUtils = new DateUtils();
        Map<String, String> extractedDates = Map.of(
                "published", "2024-04-12T09:30:00-05:00",
                "modified", "2024-04-12 09:30:00");

        Date published = dateUtils.tryToParse(extractedDates.get("published"));
        Date modified = dateUtils.tryToParse(extractedDates.get("modified"));

        assertNotNull(published);
        assertNotNull(modified);
        assertEquals("2024-04-12T14:30:00Z", DateUtils.formatDate(published));
        assertEquals("2024-04-12T09:30:00Z", DateUtils.formatDate(modified));
        assertEquals("2024-04-12T14:30:00", DateUtils.formatDateUnknownTimezone(published));
        assertNull(dateUtils.tryToParse("not-a-document-date"));
    }
}
