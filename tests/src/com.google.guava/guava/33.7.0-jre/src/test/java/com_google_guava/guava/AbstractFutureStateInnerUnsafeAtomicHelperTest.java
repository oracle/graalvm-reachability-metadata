/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class AbstractFutureStateInnerUnsafeAtomicHelperTest {
    private static final String UNSAFE_ATOMIC_HELPER_CLASS_NAME =
            "com.google.common.util.concurrent.AbstractFutureState$UnsafeAtomicHelper";

    @Test
    void unsafeAtomicHelperInitializesPrivilegedUnsafeLookup() throws Exception {
        Class<?> helperClass =
                Class.forName(
                        UNSAFE_ATOMIC_HELPER_CLASS_NAME,
                        true,
                        AbstractFutureStateInnerUnsafeAtomicHelperTest.class.getClassLoader());

        SettableFuture<String> future = SettableFuture.create();

        assertEquals("UnsafeAtomicHelper", helperClass.getSimpleName());
        assertTrue(future.set("computed"));
        assertEquals("computed", future.get(1, TimeUnit.SECONDS));
    }
}
