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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises literal-pattern quoting to search messages for user-supplied terms.
 */
public class LiteralSearchTest {

    @Test
    @Timeout(60)
    void findsMessagesContainingLiteralUserTerm() {
        List<String> messages = List.of(
                "checkout total=5+2? currency=USD",
                "checkout total=5x2? currency=USD",
                "checkout total=5+2? currency=EUR");
        MessageSearch search = new MessageSearch("5+2?");

        List<String> matches = search.find(messages);

        assertEquals(
                List.of(
                        "checkout total=5+2? currency=USD",
                        "checkout total=5+2? currency=EUR"),
                matches);
    }

    /** Application search that treats the user's term as text, not as a regexp. */
    private static final class MessageSearch {

        private final Pattern literalTerm;

        MessageSearch(String term) {
            literalTerm = Pattern.compile(Pattern.quote(term));
        }

        List<String> find(List<String> messages) {
            return messages.stream()
                    .filter(message -> {
                        Matcher matcher = literalTerm.matcher(message);
                        return matcher.find();
                    })
                    .toList();
        }
    }
}
