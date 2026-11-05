/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's locale-negotiation subsystem to choose a checkout language for each request.
public class CheckoutLocaleNegotiationTest {
    @Test
    public void choosesTheClosestSupportedCheckoutLocale() {
        CheckoutLocaleResolver resolver = new CheckoutLocaleResolver();

        assertEquals(ULocale.CANADA_FRENCH, resolver.resolve("fr-FR"));
        assertEquals(ULocale.GERMANY, resolver.resolve("de-AT"));
        assertEquals(ULocale.US, resolver.resolve("es-MX"));
    }

    private static final class CheckoutLocaleResolver {
        private final LocaleMatcher matcher = LocaleMatcher.builder()
                .setSupportedULocales(List.of(ULocale.US, ULocale.CANADA_FRENCH, ULocale.GERMANY))
                .setDefaultULocale(ULocale.US)
                .build();

        private ULocale resolve(String requestedLanguageTag) {
            return matcher.getBestMatch(ULocale.forLanguageTag(requestedLanguageTag));
        }
    }
}
