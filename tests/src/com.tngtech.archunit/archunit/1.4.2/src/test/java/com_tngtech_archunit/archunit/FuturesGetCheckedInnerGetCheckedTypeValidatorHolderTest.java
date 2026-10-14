/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.util.concurrent.Futures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class FuturesGetCheckedInnerGetCheckedTypeValidatorHolderTest {
    @Test
    void validatesCheckedExceptionTypeBeforeReturningCompletedValue() throws Exception {
        String value = Futures.getChecked(Futures.immediateFuture("ready"), IOException.class);

        assertThat(value).isEqualTo("ready");
    }
}
