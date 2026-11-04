/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene_lucene_core;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's suggestion subsystem to provide popularity-ranked product autocomplete.
public class SuggestionCatalogTest {
    @Test
    @Timeout(60)
    public void suggestsPopularProductsForPartialCatalogSearch() throws IOException {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(directory, analyzer)) {
            suggester.build(new CatalogSuggestions());

            List<LookupResult> suggestions = suggester.lookup("cordless dr", false, 2);

            assertEquals(2, suggestions.size());
            assertEquals("cordless drill", suggestions.get(0).key.toString());
            assertEquals(125L, suggestions.get(0).value);
            assertEquals("cordless drill bits", suggestions.get(1).key.toString());
            assertEquals(80L, suggestions.get(1).value);
        }
    }

    private static final class CatalogSuggestions implements InputIterator {
        private final List<String> keys = Arrays.asList(
                "cordless drill",
                "cordless drill bits",
                "garden hose reel");
        private final List<Long> popularity = Arrays.asList(125L, 80L, 90L);
        private final Iterator<String> entries = keys.iterator();
        private int currentEntry = -1;

        @Override
        public BytesRef next() {
            if (!entries.hasNext()) {
                return null;
            }
            currentEntry++;
            return new BytesRef(entries.next());
        }

        @Override
        public long weight() {
            return popularity.get(currentEntry);
        }

        @Override
        public BytesRef payload() {
            return null;
        }

        @Override
        public boolean hasPayloads() {
            return false;
        }

        @Override
        public Set<BytesRef> contexts() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasContexts() {
            return false;
        }
    }
}
