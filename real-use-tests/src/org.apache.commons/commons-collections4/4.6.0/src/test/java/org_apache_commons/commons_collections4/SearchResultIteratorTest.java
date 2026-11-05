/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the iterator subsystem for merging paginated search results.
 */
public class SearchResultIteratorTest {
    @Test
    @Timeout(60)
    public void mergesSortedResultPagesIntoOneSearchResponse() {
        List<SearchResult> firstPage = List.of(
                new SearchResult(1, "R-101", "alpha"),
                new SearchResult(4, "R-104", "delta"),
                new SearchResult(8, "R-108", "theta"));
        List<SearchResult> secondPage = List.of(
                new SearchResult(2, "R-102", "beta"),
                new SearchResult(3, "R-103", "gamma"),
                new SearchResult(7, "R-107", "eta"));
        Comparator<SearchResult> resultOrder = Comparator.comparingInt(SearchResult::rank)
                .thenComparing(SearchResult::id);

        List<SearchResult> mergedResults = IteratorUtils.toList(IteratorUtils.collatedIterator(
                resultOrder, firstPage.iterator(), secondPage.iterator()));

        assertEquals(List.of("R-101", "R-102", "R-103", "R-104", "R-107", "R-108"),
                mergedResults.stream().map(SearchResult::id).toList());
        assertEquals("gamma", mergedResults.get(2).title());
        assertEquals(6, mergedResults.size());
    }

    private record SearchResult(int rank, String id, String title) {
    }
}
