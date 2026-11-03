/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.CheckedFunctionUtils;
import io.github.resilience4j.core.functions.CheckedFunction;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recovers checked failures through Resilience4j's checked-function subsystem.
 *
 * An invoice service reads invoice records from a checked-failure data source and turns a missing
 * record into an empty invoice, so its checkout flow can continue without handling the exception.
 */
class CheckedFunctionRecoveryTest {

    @Test
    void recoversMissingInvoiceAndKeepsSuccessfulLookup() throws Throwable {
        CheckedFunction<String, Invoice> invoiceLookup = invoiceId -> {
            if (invoiceId.equals("invoice-42")) {
                return new Invoice(invoiceId, 1250);
            }
            throw new IOException("invoice not found");
        };
        CheckedFunction<String, Invoice> resilientLookup = CheckedFunctionUtils.recover(
                invoiceLookup,
                IOException.class,
                failure -> new Invoice("empty", 0));

        assertThat(resilientLookup.apply("invoice-42"))
                .isEqualTo(new Invoice("invoice-42", 1250));
        assertThat(resilientLookup.apply("invoice-missing"))
                .isEqualTo(new Invoice("empty", 0));
    }

    private record Invoice(String id, int amountInCents) {
    }
}
