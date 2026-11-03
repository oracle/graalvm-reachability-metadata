/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.CallableUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Composes and recovers blocking operations through Resilience4j's callable utility subsystem.
 *
 * A quote service transforms a fetched quote into the checkout display text and uses a cached
 * message when the remote quote endpoint reports an I/O failure.
 */
class CallableRecoveryTest {

    @Test
    void transformsSuccessfulQuoteAndRecoversRemoteFailure() throws Exception {
        String requestedSku = "SKU-42";
        Callable<String> quoteLookup = () -> {
            if (requestedSku.equals("SKU-42")) {
                return "available";
            }
            throw new IOException("quote endpoint unavailable");
        };
        Callable<String> recoveredQuote = CallableUtils.recover(
                quoteLookup,
                IOException.class,
                failure -> "cached");
        Callable<String> displayText = CallableUtils.andThen(
                recoveredQuote,
                quote -> "quote:" + quote);

        assertThat(displayText.call()).isEqualTo("quote:available");

        Callable<String> failedLookup = () -> {
            throw new IOException("quote endpoint unavailable");
        };
        Callable<String> fallbackDisplay = CallableUtils.andThen(
                CallableUtils.recover(failedLookup, IOException.class, failure -> "cached"),
                quote -> "quote:" + quote);

        assertThat(fallbackDisplay.call()).isEqualTo("quote:cached");
    }
}
