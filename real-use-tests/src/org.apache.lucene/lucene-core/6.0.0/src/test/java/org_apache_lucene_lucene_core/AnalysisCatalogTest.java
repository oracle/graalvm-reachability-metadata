/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene_lucene_core;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's analysis and tokenization subsystem for an application's search input.
public class AnalysisCatalogTest {
    @Test
    @Timeout(60)
    public void tokenizesCatalogSearchInputWithTermsAndOffsets() throws IOException {
        try (WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                TokenStream tokens = analyzer.tokenStream("description", "Cordless Drill 42")) {
            CharTermAttribute terms = tokens.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsets = tokens.addAttribute(OffsetAttribute.class);
            List<String> observedTerms = new ArrayList<>();
            List<String> observedOffsets = new ArrayList<>();

            tokens.reset();
            while (tokens.incrementToken()) {
                observedTerms.add(terms.toString());
                observedOffsets.add(offsets.startOffset() + "-" + offsets.endOffset());
            }
            tokens.end();

            assertEquals(Arrays.asList("Cordless", "Drill", "42"), observedTerms);
            assertEquals(Arrays.asList("0-8", "9-14", "15-17"), observedOffsets);
        }
    }
}
