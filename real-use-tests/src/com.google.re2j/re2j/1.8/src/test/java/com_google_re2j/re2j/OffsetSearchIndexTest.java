/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the offset-search subsystem to resume indexing product references in an event stream.
 */
public class OffsetSearchIndexTest {

    @Test
    @Timeout(60)
    void indexesOnlyReferencesAtOrAfterTheResumeOffset() {
        String stream = "event=order-created sku=OLD-0001\n"
                + "event=order-updated sku=NEW-1042\n"
                + "event=order-updated sku=NEW-1043\n";
        int resumeOffset = stream.indexOf("event=order-updated");
        ProductReferenceIndex index = new ProductReferenceIndex();

        List<String> references = index.readFrom(stream, resumeOffset);

        assertEquals(List.of("NEW-1042", "NEW-1043"), references);
    }

    /** Application indexer that resumes a product-reference scan after a stored stream offset. */
    private static final class ProductReferenceIndex {

        private final Pattern productReference = Pattern.compile("sku=(?<sku>[A-Z]+-[0-9]+)");

        List<String> readFrom(String stream, int resumeOffset) {
            Matcher matcher = productReference.matcher(stream);
            List<String> references = new ArrayList<>();
            int nextOffset = resumeOffset;
            while (matcher.find(nextOffset)) {
                references.add(matcher.group("sku"));
                nextOffset = matcher.end();
            }
            return references;
        }
    }
}
