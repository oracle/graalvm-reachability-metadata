/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class AbstractFutureStateInnerUnsafeAtomicHelperTest {
    @Test
    void settableFuturePublishesAValueThroughItsAtomicStateHelper() throws Exception {
        SettableFuture<String> future = SettableFuture.create();

        assertThat(future.set("computed")).isTrue();
        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("computed");
        assertThat(future.isDone()).isTrue();
    }
}
