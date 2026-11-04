/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.ElementsAccessor;
import ognl.Ognl;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's elements-accessor subsystem for rendering a product search result page.
 *
 * A search application exposes its paged result container to OGNL templates, allowing a result
 * list to display matching product names without exposing the container's storage details.
 */
public class SearchResultsElementsAccessorTest {
    @Test
    @Timeout(60)
    void rendersProductNamesFromApplicationSearchResults() throws OgnlException {
        SearchPage page = new SearchPage(new SearchResults(List.of(
                new SearchHit("Travel mug", 0.98),
                new SearchHit("Insulated bottle", 0.91))));
        ElementsAccessor previousAccessor = OgnlRuntime.getElementsAccessor(SearchResults.class);
        OgnlRuntime.setElementsAccessor(SearchResults.class, new SearchResultsAccessor());

        try {
            Map context = Ognl.createDefaultContext(page);
            Object productNames = Ognl.getValue("results.{name}", context, page);

            assertThat(productNames).asList().containsExactly("Travel mug", "Insulated bottle");
        } finally {
            OgnlRuntime.setElementsAccessor(SearchResults.class, previousAccessor);
        }
    }

    public static final class SearchResultsAccessor implements ElementsAccessor {
        @Override
        public Enumeration getElements(Object target) throws OgnlException {
            return Collections.enumeration(((SearchResults) target).hits());
        }
    }

    public static final class SearchPage {
        private final SearchResults results;

        private SearchPage(SearchResults results) {
            this.results = results;
        }

        public SearchResults getResults() {
            return results;
        }
    }

    public static final class SearchResults {
        private final List<SearchHit> hits;

        private SearchResults(List<SearchHit> hits) {
            this.hits = hits;
        }

        public List<SearchHit> hits() {
            return hits;
        }
    }

    public static final class SearchHit {
        private final String name;
        private final double score;

        private SearchHit(String name, double score) {
            this.name = name;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public double getScore() {
            return score;
        }
    }
}
