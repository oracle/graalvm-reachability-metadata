/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.StringSearch;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.text.StringCharacterIterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's text-search subsystem to find accent-insensitive terms in help-center articles.
public class HelpCenterStringSearchTest {
    @Test
    public void findsAllMatchesInAHelpCenterArticle() {
        HelpCenterSearch search = new HelpCenterSearch(
                "To request a refund, open the refund form. A resume is not required.");

        assertEquals("refund", search.firstMatch("refund"));
        assertEquals("refund", search.nextMatch("refund"));
        assertEquals("resume", search.firstMatch("r\u00e9sum\u00e9"));
    }

    private static final class HelpCenterSearch {
        private final String article;
        private final RuleBasedCollator collator;

        private HelpCenterSearch(String article) {
            this.article = article;
            collator = (RuleBasedCollator) Collator.getInstance(ULocale.ENGLISH);
            collator.setStrength(Collator.PRIMARY);
        }

        private String firstMatch(String term) {
            StringSearch search = createSearch(term);
            return search.first() == StringSearch.DONE ? "" : search.getMatchedText();
        }

        private String nextMatch(String term) {
            StringSearch search = createSearch(term);
            search.first();
            return search.next() == StringSearch.DONE ? "" : search.getMatchedText();
        }

        private StringSearch createSearch(String term) {
            return new StringSearch(term, new StringCharacterIterator(article), collator);
        }
    }
}
