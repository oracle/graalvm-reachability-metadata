/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.ResultUtils;
import io.github.resilience4j.core.functions.Either;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classifies typed operation results with Resilience4j's result-handling subsystem.
 *
 * A payment service represents authorization success and failure as an Either, then uses
 * Resilience4j's result predicates to decide whether to issue a receipt or show a decline.
 */
class TypedResultHandlingTest {

    @Test
    void classifiesAuthorizationOutcomesForThePaymentFlow() {
        Either<Throwable, Authorization> approved = Either.right(
                new Authorization("auth-17", "receipt-42"));
        Either<Throwable, Authorization> declined = Either.left(
                new IllegalStateException("issuer unavailable"));

        boolean approvedForReceipt = ResultUtils.isSuccessfulAndReturned(
                approved,
                Authorization.class,
                authorization -> authorization.receiptId().startsWith("receipt-"));
        boolean declinedByIssuer = ResultUtils.isFailedAndThrown(
                declined,
                IllegalStateException.class,
                failure -> failure.getMessage().contains("issuer"));

        assertThat(approvedForReceipt).isTrue();
        assertThat(declinedByIssuer).isTrue();
        assertThat(approved.map(Authorization::receiptId).get()).isEqualTo("receipt-42");
        assertThat(declined.getOrNull()).isNull();
    }

    private record Authorization(String authorizationId, String receiptId) {
    }
}
