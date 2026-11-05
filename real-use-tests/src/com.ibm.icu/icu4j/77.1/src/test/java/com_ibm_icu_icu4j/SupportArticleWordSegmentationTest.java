/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's word-segmentation subsystem to extract searchable words from a support article.
public class SupportArticleWordSegmentationTest {
    @Test
    public void extractsWordsFromSupportArticleText() {
        SupportArticle article = new SupportArticle();

        assertEquals(
                List.of("Order", "status", "ready", "for", "pickup"),
                article.searchableWords());
    }

    private static final class SupportArticle {
        private final String text = "Order status: ready for pickup.";

        private List<String> searchableWords() {
            BreakIterator words = BreakIterator.getWordInstance(ULocale.ENGLISH);
            words.setText(text);

            List<String> searchableWords = new ArrayList<>();
            int start = words.first();
            for (int end = words.next(); end != BreakIterator.DONE; start = end, end = words.next()) {
                if (words.getRuleStatus() != BreakIterator.WORD_NONE) {
                    searchableWords.add(text.substring(start, end));
                }
            }
            return searchableWords;
        }
    }
}
