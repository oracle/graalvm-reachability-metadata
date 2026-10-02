/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.concurrent.TimeUnit;

import org.apache.curator.shaded.com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFutureInnerUnsafeAtomicHelperAnonymous1Test {
    @Test
    void settableFutureCompletesThroughAtomicHelper() throws Exception {
        SettableFuture<String> future = SettableFuture.create();

        assertThat(future.set("computed")).isTrue();
        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("computed");
    }
}
