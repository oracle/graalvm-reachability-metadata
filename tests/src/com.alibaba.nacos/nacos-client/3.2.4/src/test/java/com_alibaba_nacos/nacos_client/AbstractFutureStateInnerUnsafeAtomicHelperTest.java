/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.SettableFuture;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class AbstractFutureStateInnerUnsafeAtomicHelperTest {
    @Test
    void completesChainedFuturesAndNotifiesListeners() throws Exception {
        SettableFuture<String> source = SettableFuture.create();
        SettableFuture<String> dependent = SettableFuture.create();
        AtomicInteger calls = new AtomicInteger();
        dependent.addListener(calls::incrementAndGet, MoreExecutors.directExecutor());
        assertTrue(dependent.setFuture(source));
        assertTrue(source.set("configuration"));
        assertEquals("configuration", dependent.get(30, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }
}
