/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's HTML sanitization subsystem for user-submitted review markup.
 */
public class ReviewMarkupSanitizationTest {

    @Test
    @Timeout(60)
    public void sanitizesReviewMarkupBeforeItIsDisplayed() {
        String submittedReview = """
                <p>Great <mark>sound</mark> <a href="/guide" target="_blank">guide</a>.</p><script>stealCustomerData()</script><p onclick="stealCustomerData()">Easy to follow.</p>
                """;
        Safelist reviewPolicy = Safelist.basic()
                .addTags("mark")
                .addAttributes("a", "target");

        String sanitizedReview = Jsoup.clean(
                submittedReview,
                "https://reviews.example.test/products/7",
                reviewPolicy);

        assertEquals("""
                <p>Great <mark>sound</mark> <a href="https://reviews.example.test/guide" target="_blank">guide</a>.</p>
                <p>Easy to follow.</p>\
                """, sanitizedReview);
        assertTrue(sanitizedReview.contains("<mark>sound</mark>"));
        assertTrue(sanitizedReview.contains("href=\"https://reviews.example.test/guide\""));
        assertFalse(sanitizedReview.contains("script"));
        assertFalse(sanitizedReview.contains("onclick"));
        assertFalse(Jsoup.isValid(submittedReview, reviewPolicy));
    }
}
