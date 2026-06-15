/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hadoop.thirdparty.com.google.common.util.concurrent.MoreExecutors;
import org.apache.hadoop.thirdparty.com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.Test;

public class AbstractFutureInnerUnsafeAtomicHelperAnonymous1Test {
    @Test
    void settableFutureCompletesAndRunsListener() throws Exception {
        SettableFuture<String> future = SettableFuture.create();
        AtomicBoolean listenerRan = new AtomicBoolean();
        future.addListener(() -> listenerRan.set(true), MoreExecutors.directExecutor());

        assertThat(future.set("completed")).isTrue();

        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("completed");
        assertThat(future.isDone()).isTrue();
        assertThat(listenerRan).isTrue();
    }
}
